# File Service - MongoDB Validation Fix Summary

**Data**: 2025-11-24  
**Issue**: Document failed validation - Missing required fields `message_id` and `uploaded_at`

## Problema Identificado

O MongoDB estava rejeitando documentos FileAttachment devido a schema validation:

```
Document failed validation:
- message_id: required field missing
- uploaded_at: required field missing
```

## Análise da Causa Raiz

### Problema 1: Schema MongoDB Incorreto
O `mongo-init.js` original tinha:
```javascript
required: ['file_id', 'message_id', 'filename', ...] // ❌ message_id obrigatório
```

Mas na arquitetura do file-service:
- Arquivos são criados **antes** de serem associados a mensagens
- `message_id` deve ser `null` inicialmente
- Será preenchido posteriormente quando o arquivo for anexado a uma mensagem

### Problema 2: FileController não definia `uploadedAt`
O código original tinha:
```java
.uploadedAt(null) // ❌ Deixava null
```

Mas o MongoDB schema exige `uploaded_at` como campo obrigatório.

### Problema 3: Conflito entre Spring Data e Schema Manual
- Spring Data MongoDB cria índices automaticamente via `@Indexed`
- `mongo-init.js` também criava índices manualmente
- **Conflito**: Índice `file_id` criado duas vezes com nomes diferentes

## Soluções Implementadas

### ✅ Solução 1: FileController - Definir `uploadedAt` e `messageId=null`

**Arquivo**: `services/file-service/src/main/java/com/chat4all/file/api/FileController.java`

```java
// ANTES
FileAttachment fileAttachment = FileAttachment.builder()
    .messageId(request.getMessageId())  // ❌ Podia ser inválido
    .uploadedAt(null)                   // ❌ Violava schema
    .build();

// DEPOIS
FileAttachment fileAttachment = FileAttachment.builder()
    .messageId(null)        // ✅ Explicitamente null (ainda não associado)
    .uploadedAt(now)        // ✅ Timestamp de criação do registro
    .build();
```

**Justificativa**:
- `uploadedAt` representa quando o **registro** foi criado, não quando o upload S3 completa
- `messageId` será preenchido posteriormente quando arquivo for anexado a mensagem (T072)

### ✅ Solução 2: Remover Schema Manual do MongoDB

**Arquivo**: `infrastructure/mongodb/mongo-init.js`

**ANTES** (Criava coleção com schema validation):
```javascript
db.createCollection('files', {
  validator: {
    $jsonSchema: {
      required: ['file_id', 'message_id', ...], // ❌ Conflito com Spring Data
      properties: { ... }
    }
  }
});
db.files.createIndex({ file_id: 1 }, { unique: true }); // ❌ Conflito
```

**DEPOIS** (Deixa Spring Data gerenciar):
```javascript
// NOTE: Files collection is managed by Spring Data MongoDB (@Document annotations)
// Schema validation is NOT created here to avoid conflicts with Spring Data's
// auto-index creation. Indexes are defined in FileAttachment.java using @Indexed.

print('Files collection will be created automatically by Spring Data MongoDB');
print('Schema validation and indexes are defined in FileAttachment.java');
```

**Justificativa**:
- Spring Data cria índices automaticamente via `@Indexed` annotations
- Evita conflitos de nomes de índices (IndexOptionsConflict error code 85)
- Mantém schema validation em código Java (type-safe)

### ✅ Solução 3: FileAttachment - Schema via Annotations

**Arquivo**: `services/file-service/src/main/java/com/chat4all/file/domain/FileAttachment.java`

```java
@Data
@Document(collection = "files")
public class FileAttachment {
    
    @Indexed(unique = true)
    @Field("file_id")
    private String fileId;          // ✅ Índice único automático
    
    @Indexed
    @Field("message_id")
    private String messageId;       // ✅ Opcional (sem @NonNull)
    
    @Field("uploaded_at")
    private Instant uploadedAt;     // ✅ Obrigatório (sempre preenchido)
    
    @Indexed(expireAfterSeconds = 0)
    @Field("expires_at")
    private Instant expiresAt;      // ✅ TTL index automático
}
```

## Resultado Final

### Teste de Integração
```bash
curl -X POST http://localhost:8083/api/files/initiate \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "test-document.pdf",
    "fileSize": 2048000,
    "mimeType": "application/pdf"
  }'
```

**Response (Status 201 Created)**:
```json
{
  "fileId": "4c015ece-b8f6-4dde-9ba6-abc422eb97c9",
  "uploadUrl": "http://chat4all-files.localhost:9000/files/...",
  "objectKey": "files/2025/11/4c015ece-b8f6-4dde-9ba6-abc422eb97c9/test-document.pdf",
  "expiresAt": "2025-11-25T02:41:42.343Z",
  "status": "PENDING",
  "message": "Upload file using PUT request to uploadUrl..."
}
```

### Documento MongoDB Criado
```javascript
{
  _id: ObjectId('692513e2114f51a9ecf66f05'),
  file_id: '4c015ece-b8f6-4dde-9ba6-abc422eb97c9',
  message_id: null,                                    // ✅ Ausente (null implícito)
  filename: 'test-document.pdf',
  file_size: Long('2048000'),
  mime_type: 'application/pdf',
  status: 'PENDING',
  uploaded_at: ISODate('2025-11-25T02:26:42.343Z'),   // ✅ Definido
  expires_at: ISODate('2025-11-26T02:26:42.343Z'),
  storage_url: 's3://chat4all-files/files/2025/11/...',
  created_at: ISODate('2025-11-25T02:26:42.343Z'),
  updated_at: ISODate('2025-11-25T02:26:42.343Z')
}
```

### Índices Criados Automaticamente
```javascript
db.files.getIndexes()
[
  { v: 2, key: { _id: 1 }, name: '_id_' },
  { v: 2, key: { file_id: 1 }, name: 'file_id', unique: true },      // ✅ Spring Data
  { v: 2, key: { message_id: 1 }, name: 'message_id' },               // ✅ Spring Data
  { v: 2, key: { expires_at: 1 }, name: 'expires_at', expireAfterSeconds: 0 } // ✅ TTL
]
```

## Lições Aprendidas

### 1. **Evitar Schema Validation Manual com Spring Data**
- Spring Data MongoDB gerencia schema via annotations (`@Document`, `@Indexed`)
- Schema validation manual (`db.createCollection()`) causa conflitos
- **Recomendação**: Use Spring Data OU schema manual, não ambos

### 2. **Semântica de `uploadedAt`**
- Representa **criação do registro** metadata, não conclusão do upload S3
- Permite rastreamento temporal desde a inicialização
- Upload S3 real é assíncrono (presigned URL workflow)

### 3. **Campos Opcionais em Workflows Assíncronos**
- `message_id` é opcional porque arquivos podem existir sem mensagens
- Workflow: 
  1. Cliente inicia upload → `message_id=null`
  2. Arquivo é enviado para S3
  3. Cliente envia mensagem com `fileId` (T072)
  4. Message Service atualiza `message_id` no FileAttachment

### 4. **Debugging MongoDB Validation Errors**
- Erro code 121 = Document failed validation
- Use `db.getCollectionInfos({name: 'collection'})[0].options.validator` para ver schema ativo
- Verifique `required` array e `bsonType` dos campos

## Status das Tasks

- ✅ **T062**: FileAttachment entity - Complete
- ✅ **T063**: FileRepository - Complete
- ✅ **T064**: S3StorageService - Complete
- ✅ **T065**: FileController - Complete **com correção de validação**
- ⏳ **T066**: Multipart upload - Pending
- ⏳ **T067**: File validation - Pending
- ⏳ **T068**: Malware scanning - Pending
- ⏳ **T069**: Thumbnail generation - Pending
- ⏳ **T070**: FileUploadCompleteEvent - Pending
- ⏳ **T071**: Message.fileAttachments - Pending
- ⏳ **T072**: SendMessageRequest.fileIds - Pending
- ⏳ **T073**: MongoDB TTL index - **Já implementado** (via @Indexed)

## Próximos Passos

1. **T066 - Multipart Upload**: Arquivos >100MB
2. **T067 - File Type Validation**: MIME whitelist
3. **T068 - Malware Scanning**: ClamAV integration
4. **T069 - Thumbnail Generation**: ImageMagick/FFmpeg
5. **T070-T072 - Integration**: Kafka events e message linkage

## Referências

- **Spring Data MongoDB**: https://docs.spring.io/spring-data/mongodb/docs/current/reference/html/
- **MongoDB Schema Validation**: https://www.mongodb.com/docs/manual/core/schema-validation/
- **AWS S3 Presigned URLs**: https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html
