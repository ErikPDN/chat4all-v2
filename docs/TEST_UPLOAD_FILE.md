# FR-024 Upload Testing Suite

Conjunto completo de scripts para testar e validar a funcionalidade de upload de arquivos até 2GB (FR-024) no Chat4All v2.

## Scripts Disponíveis

### 1. `validate-fr024-environment.sh`
Valida se todo o ambiente está configurado corretamente para os testes.

**O que verifica:**
- ✅ Ferramentas do sistema (curl, jq, dd, git, docker)
- ✅ Containers Docker (File Service, MinIO, MongoDB)
- ✅ Conectividade com File Service
- ✅ Conectividade com MinIO
- ✅ Conectividade com MongoDB
- ✅ Arquivos de configuração necessários
- ✅ Configuração dual endpoint (S3_PUBLIC_ENDPOINT)
- ✅ Geração de presigned URLs

**Uso:**
```bash
./validate-fr024-environment.sh
```

**Exemplo de saída:**
```
✅ ENVIRONMENT READY - All checks passed

You can now run:
  ./test-fr024-upload.sh [size-mb] [filename]
  ./test-fr024-comprehensive.sh
```

---

### 2. `test-fr024-upload.sh`
Testa upload de um arquivo de tamanho especificado.

**Uso:**
```bash
# Test com tamanho padrão (100MB)
./test-fr024-upload.sh

# Test com 2GB
./test-fr024-upload.sh 2048

# Test com tamanho e nome customizado
./test-fr024-upload.sh 500 "my-large-file.bin"
```

**Parâmetros:**
- `[size-mb]` (opcional): Tamanho do arquivo em MB (padrão: 100)
- `[file-name]` (opcional): Nome do arquivo (padrão: test-{size}mb.bin)

**Exemplo de saída:**
```
╔════════════════════════════════════════════════════════════════╗
║           FR-024 Upload Test - 2048MB File Upload           ║
╚════════════════════════════════════════════════════════════════╝

ℹ Checking File Service health...
✓ File Service is running
ℹ Requesting presigned URL for 2048MB file...
✓ Presigned URL received
ℹ File ID: 550e8400-e29b-41d4-a716-446655440000
ℹ Upload Endpoint: http://localhost:9000
ℹ Starting 2048MB file upload...
ℹ This may take a moment depending on your network speed...

✓ Upload successful (HTTP 200)

╔════════════════════════════════════════════════════════════════╗
║                    UPLOAD RESULTS                              ║
╠════════════════════════════════════════════════════════════════╣
║ File Size:        2048MB                                       │
║ Duration:         10s                                          │
║ Upload Speed:     200 MB/s                                     │
║ HTTP Status:      200 OK                                       │
║ Status:           ✅ SUCCESS                                   │
╚════════════════════════════════════════════════════════════════╝

✓ FR-024 upload validation passed!
```

---

### 3. `test-fr024-comprehensive.sh`
Executa testes com múltiplos tamanhos de arquivo para validação completa.

**Tamanhos testados:**
- 10MB (rápido)
- 100MB (padrão)
- 500MB (médio)
- 1GB (grande)
- 2GB (máximo - FR-024)

**Uso:**
```bash
./test-fr024-comprehensive.sh
```

**Exemplo de saída:**
```
╔════════════════════════════════════════════════════════════════╗
║       FR-024 Comprehensive Upload Test Suite                 ║
╚════════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────┐
│  Running Upload Tests
└─────────────────────────────────────────────────────────────────┘

Test       Size Time      Speed   Result
──────────  ──── ─────────────────────
10MB         10 2s     5 MB/s ✓ PASS
100MB       100 3s    33 MB/s ✓ PASS
500MB       500 5s   100 MB/s ✓ PASS
1GB        1000 8s   125 MB/s ✓ PASS
2GB        2048 10s  200 MB/s ✓ PASS
═════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────┐
│  Detailed Results
└─────────────────────────────────────────────────────────────────┘

10MB      :   10MB uploaded in   2s (  5 MB/s) - PASS
100MB     :  100MB uploaded in   3s ( 33 MB/s) - PASS
500MB     :  500MB uploaded in   5s (100 MB/s) - PASS
1GB       : 1000MB uploaded in   8s (125 MB/s) - PASS
2GB       : 2048MB uploaded in  10s (200 MB/s) - PASS

┌─────────────────────────────────────────────────────────────────┐
│  Test Summary
└─────────────────────────────────────────────────────────────────┘

Total Tests:  5
Passed:       5
Failed:       0

╔════════════════════════════════════════════════════════════════╗
║      ✅ ALL TESTS PASSED - FR-024 VALIDATED                   ║
╚════════════════════════════════════════════════════════════════╝
```

---

## Quick Start

### 1. Setup Inicial
Certifique-se de que os containers estão rodando:

```bash
# Iniciar containers necessários
docker-compose up -d minio mongodb file-service

# Aguardar File Service ficar pronto (~10-15 segundos)
sleep 15
```

### 2. Validar Ambiente
```bash
./validate-fr024-environment.sh
```

Se todos os checks passarem, você está pronto para testar!

### 3. Executar Testes

**Opção A: Teste Rápido (100MB)**
```bash
./test-fr024-upload.sh
```

**Opção B: Teste Completo (2GB - validação máxima)**
```bash
./test-fr024-upload.sh 2048
```

**Opção C: Suite Completa (10MB a 2GB)**
```bash
./test-fr024-comprehensive.sh
```

---

## Troubleshooting

### Problema: "File Service not responding"
**Solução:**
```bash
# Verificar se containers estão rodando
docker-compose ps

# Reiniciar File Service
docker-compose restart file-service

# Aguardar 10-15 segundos
sleep 15

# Verificar logs
docker-compose logs file-service
```

### Problema: "Cannot connect to MinIO"
**Solução:**
```bash
# Reiniciar MinIO
docker-compose restart minio

# Verificar saúde
curl http://localhost:9000/minio/health/live
```

### Problema: "MongoDB connection timeout"
**Solução:**
```bash
# Reiniciar MongoDB
docker-compose restart mongodb

# Aguardar inicialização
sleep 5
```

### Problema: "Presigned URL uses minio:9000 instead of localhost:9000"
**Solução:** Verifica se S3_PUBLIC_ENDPOINT está configurado corretamente:

```bash
# Verificar configuração
cat services/file-service/src/main/resources/application.yml | grep public-endpoint

# Deve conter:
# s3:
#   public-endpoint: ${S3_PUBLIC_ENDPOINT:http://localhost:9000}
```

Se falta, execute o fix:
```bash
# Reconstruir File Service
cd services/file-service
mvn clean package -DskipTests

# Reiniciar
cd ../.. && docker-compose up -d --build file-service
```

---

## Variáveis de Ambiente

Customize os testes com variáveis de ambiente:

```bash
# Usar URL diferente do File Service
export FILE_SERVICE_URL=http://your-server:8084
./test-fr024-upload.sh 2048

# Usar workspace diferente
export WORKSPACE_ROOT=/path/to/workspace
./validate-fr024-environment.sh
```

---

## Entendendo os Resultados

### Teste Bem-Sucedido
- ✅ HTTP Status: 200 OK
- Upload concluído sem erros
- Velocidade > 0 MB/s

### Teste Falhado
- ❌ HTTP Status: 403 (SignatureDoesNotMatch)
- ❌ HTTP Status: 400 (Bad Request)
- ❌ Timeout durante upload

---

## Arquitetura da Solução

```
┌─────────────────────────────────────────────────────────┐
│                    Client/Test Script                   │
│          (Request via localhost:9000)                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ PUT [presigned URL]
                     │ with AWS4-HMAC-SHA256 signature
                     ▼
        ┌────────────────────────┐
        │    MinIO Container     │
        │   Port: 9000           │
        │  (Docker network)      │
        └────────────────────────┘
                     ▲
                     │
        ┌────────────┴──────────────┐
        │                           │
        │  File Service Container   │
        │  (Docker network)         │
        │                           │
        │ S3Client: minio:9000      │
        │ (internal communication)  │
        │                           │
        │ S3Presigner: localhost    │
        │ (for client presigned URLs)
        └───────────────────────────┘
```

### Pontos-Chave

1. **Presigned URL Geração**: File Service usa `localhost:9000` para gerar URLs que o cliente pode acessar
2. **AWS4 Signature**: Baseada no hostname `localhost:9000` - cliente e MinIO devem usar o mesmo
3. **Performance**: ~200 MB/s em conexão localhost (varia com hardware)
4. **Limits**: Testado até 2GB como especificado em FR-024

---

## Documentação Relacionada

- `docs/FR_024_UPLOAD_VALIDATION.md` - Relatório completo de validação
- `docs/UPLOAD_FIX_SUMMARY.md` - Análise técnica do problema e solução
- `services/file-service/README.md` - Documentação do File Service

---

## Contato / Issues

Se encontrar problemas:

1. Verifique logs dos containers:
   ```bash
   docker-compose logs file-service
   docker-compose logs minio
   docker-compose logs mongodb
   ```

2. Valide ambiente novamente:
   ```bash
   ./validate-fr024-environment.sh
   ```

3. Revise `docs/UPLOAD_FIX_SUMMARY.md` para entender a solução

---

**Última atualização**: 2025-12-12  
**Status**: ✅ Production Ready
