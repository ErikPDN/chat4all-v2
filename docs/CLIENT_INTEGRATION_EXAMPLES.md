# Chat4All v2 - Client Integration Examples

**Document Purpose**: Code examples for integrating with Chat4All authentication and messaging  
**Last Updated**: 2025-12-10  
**Status**: ✅ Complete with Examples

---

## Table of Contents

1. [JavaScript/TypeScript (Web)](#javascripttypescript-web)
2. [React.js](#reactjs)
3. [Java (Spring Boot)](#java-spring-boot)
4. [Python](#python)
5. [cURL (HTTP)](#curl-http)
6. [Postman Collection](#postman-collection)

---

## JavaScript/TypeScript (Web)

### Complete Client Implementation

```typescript
// chat4all-client.ts
import axios, { AxiosInstance } from 'axios';

interface AuthToken {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
}

interface Message {
  messageId: string;
  conversationId: string;
  senderId: string;
  content: string;
  type: 'TEXT' | 'IMAGE' | 'FILE' | 'VIDEO';
  status: 'PENDING' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';
  createdAt: string;
  readAt?: string;
}

interface Conversation {
  conversationId: string;
  messages: Message[];
  pagination: {
    nextCursor: string;
    hasMore: boolean;
  };
}

export class Chat4AllClient {
  private keycloakUrl: string;
  private apiGatewayUrl: string;
  private messageServiceUrl: string;
  private userServiceUrl: string;
  private clientId: string;
  
  private axiosInstance: AxiosInstance;
  private authToken?: AuthToken;
  private userId?: string;

  constructor(config: {
    keycloakUrl?: string;
    apiGatewayUrl?: string;
    messageServiceUrl?: string;
    userServiceUrl?: string;
    clientId?: string;
  } = {}) {
    this.keycloakUrl = config.keycloakUrl || 'http://localhost:8888';
    this.apiGatewayUrl = config.apiGatewayUrl || 'http://localhost:8080';
    this.messageServiceUrl = config.messageServiceUrl || 'http://localhost:8081';
    this.userServiceUrl = config.userServiceUrl || 'http://localhost:8083';
    this.clientId = config.clientId || 'chat4all-client';

    this.axiosInstance = axios.create({
      timeout: 30000,
    });

    // Add token to all requests
    this.axiosInstance.interceptors.request.use((config) => {
      if (this.authToken) {
        config.headers.Authorization = `Bearer ${this.authToken.access_token}`;
      }
      return config;
    });

    // Handle token refresh on 401
    this.axiosInstance.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;

          if (this.authToken?.refresh_token) {
            try {
              await this.refreshToken();
              return this.axiosInstance(originalRequest);
            } catch (refreshError) {
              console.error('Token refresh failed:', refreshError);
              throw new Error('Session expired. Please login again.');
            }
          }
        }

        return Promise.reject(error);
      }
    );
  }

  /**
   * Authenticate user with username and password
   */
  async login(username: string, password: string): Promise<void> {
    try {
      const response = await axios.post(
        `${this.keycloakUrl}/realms/chat4all/protocol/openid-connect/token`,
        new URLSearchParams({
          grant_type: 'password',
          client_id: this.clientId,
          username,
          password,
          scope: 'openid profile email',
        }),
        {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        }
      );

      this.authToken = response.data;

      // Get user ID
      const userProfile = await this.axiosInstance.get(
        `${this.userServiceUrl}/api/v1/users/me`
      );
      this.userId = userProfile.data.userId;

      console.log(`Logged in as: ${username}`);
    } catch (error) {
      console.error('Login failed:', error);
      throw new Error('Authentication failed. Check credentials and try again.');
    }
  }

  /**
   * Refresh access token using refresh token
   */
  async refreshToken(): Promise<void> {
    if (!this.authToken?.refresh_token) {
      throw new Error('No refresh token available');
    }

    try {
      const response = await axios.post(
        `${this.keycloakUrl}/realms/chat4all/protocol/openid-connect/token`,
        new URLSearchParams({
          grant_type: 'refresh_token',
          client_id: this.clientId,
          refresh_token: this.authToken.refresh_token,
        }),
        {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        }
      );

      this.authToken = response.data;
      console.log('Token refreshed successfully');
    } catch (error) {
      console.error('Token refresh failed:', error);
      throw error;
    }
  }

  /**
   * Send a message
   */
  async sendMessage(
    conversationId: string,
    recipientId: string,
    content: string,
    channel: 'WHATSAPP' | 'TELEGRAM' | 'INSTAGRAM' = 'WHATSAPP'
  ): Promise<Message> {
    try {
      const response = await this.axiosInstance.post(
        `${this.messageServiceUrl}/api/v1/messages`,
        {
          conversationId,
          recipientId,
          content,
          type: 'TEXT',
          channel,
        }
      );

      return response.data;
    } catch (error) {
      console.error('Failed to send message:', error);
      throw error;
    }
  }

  /**
   * Fetch conversation messages
   */
  async getConversationMessages(
    conversationId: string,
    options?: {
      limit?: number;
      before?: string;
    }
  ): Promise<Conversation> {
    try {
      const params = new URLSearchParams();
      if (options?.limit) params.append('limit', options.limit.toString());
      if (options?.before) params.append('before', options.before);

      const response = await this.axiosInstance.get(
        `${this.messageServiceUrl}/api/v1/conversations/${conversationId}/messages?${params}`
      );

      return response.data;
    } catch (error) {
      console.error('Failed to fetch messages:', error);
      throw error;
    }
  }

  /**
   * Mark conversation as read
   */
  async markAsRead(conversationId: string): Promise<void> {
    try {
      await this.axiosInstance.put(
        `${this.messageServiceUrl}/api/v1/conversations/${conversationId}/read`,
        {
          readAt: new Date().toISOString(),
        }
      );

      console.log(`Marked ${conversationId} as read`);
    } catch (error) {
      console.error('Failed to mark as read:', error);
      throw error;
    }
  }

  /**
   * Get message status
   */
  async getMessageStatus(messageId: string): Promise<any> {
    try {
      const response = await this.axiosInstance.get(
        `${this.messageServiceUrl}/api/v1/messages/${messageId}/status`
      );

      return response.data;
    } catch (error) {
      console.error('Failed to get message status:', error);
      throw error;
    }
  }

  /**
   * Connect to WebSocket for real-time messages
   */
  connectWebSocket(
    onMessage: (message: Message) => void,
    onError?: (error: any) => void
  ): WebSocket {
    if (!this.authToken) {
      throw new Error('Not authenticated');
    }

    const wsUrl = `${this.apiGatewayUrl.replace('http', 'ws')}/api/v1/ws?token=${this.authToken.access_token}`;
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log('WebSocket connected');
    };

    ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        if (message.type === 'NEW_MESSAGE') {
          onMessage(message.payload);
        }
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      if (onError) onError(error);
    };

    ws.onclose = () => {
      console.log('WebSocket closed');
    };

    return ws;
  }

  /**
   * Get current user ID
   */
  getCurrentUserId(): string {
    if (!this.userId) {
      throw new Error('User not authenticated');
    }
    return this.userId;
  }

  /**
   * Check if authenticated
   */
  isAuthenticated(): boolean {
    return !!this.authToken && !!this.userId;
  }

  /**
   * Logout (clear local token)
   */
  logout(): void {
    this.authToken = undefined;
    this.userId = undefined;
    console.log('Logged out');
  }
}

// Usage Example
async function example() {
  const client = new Chat4AllClient();

  // Login
  await client.login('alice', 'alice123');

  // Send message
  const message = await client.sendMessage(
    'conv-alice-bob-001',
    'bob-user-id',
    'Hello Bob!'
  );
  console.log('Message sent:', message.messageId);

  // Fetch messages
  const conversation = await client.getConversationMessages('conv-alice-bob-001');
  console.log('Messages:', conversation.messages);

  // Mark as read
  await client.markAsRead('conv-alice-bob-001');

  // WebSocket real-time
  const ws = client.connectWebSocket((msg) => {
    console.log('New message received:', msg);
  });

  // Check message status
  const status = await client.getMessageStatus(message.messageId);
  console.log('Message status:', status);
}
```

---

## React.js

### Hook-Based Implementation

```typescript
// useChat4All.ts - React Hook
import { useEffect, useState, useCallback, useRef } from 'react';
import { Chat4AllClient } from './chat4all-client';

interface UseChat4AllOptions {
  keycloakUrl?: string;
  messageServiceUrl?: string;
  userServiceUrl?: string;
}

export function useChat4All(options?: UseChat4AllOptions) {
  const clientRef = useRef<Chat4AllClient | null>(null);
  const wsRef = useRef<WebSocket | null>(null);

  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [messages, setMessages] = useState<any[]>([]);
  const [currentConversation, setCurrentConversation] = useState<string | null>(null);

  // Initialize client
  useEffect(() => {
    if (!clientRef.current) {
      clientRef.current = new Chat4AllClient(options);
    }
  }, [options]);

  const login = useCallback(async (username: string, password: string) => {
    setLoading(true);
    setError(null);

    try {
      const client = clientRef.current!;
      await client.login(username, password);
      setIsAuthenticated(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
      setIsAuthenticated(false);
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    const client = clientRef.current!;
    client.logout();
    setIsAuthenticated(false);
    setMessages([]);
    setCurrentConversation(null);
    
    if (wsRef.current) {
      wsRef.current.close();
    }
  }, []);

  const sendMessage = useCallback(
    async (recipientId: string, content: string, conversationId?: string) => {
      if (!isAuthenticated) {
        setError('Not authenticated');
        return;
      }

      setError(null);

      try {
        const client = clientRef.current!;
        const convId = conversationId || `conv-${Date.now()}`;

        const message = await client.sendMessage(
          convId,
          recipientId,
          content
        );

        setMessages((prev) => [...prev, message]);
        return message;
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to send message');
      }
    },
    [isAuthenticated]
  );

  const loadConversation = useCallback(
    async (conversationId: string) => {
      if (!isAuthenticated) {
        setError('Not authenticated');
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const client = clientRef.current!;
        const conversation = await client.getConversationMessages(conversationId);
        setMessages(conversation.messages);
        setCurrentConversation(conversationId);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load conversation');
      } finally {
        setLoading(false);
      }
    },
    [isAuthenticated]
  );

  const markAsRead = useCallback(
    async (conversationId: string) => {
      if (!isAuthenticated) return;

      try {
        const client = clientRef.current!;
        await client.markAsRead(conversationId);
      } catch (err) {
        console.error('Failed to mark as read:', err);
      }
    },
    [isAuthenticated]
  );

  const connectWebSocket = useCallback(() => {
    if (!isAuthenticated) {
      setError('Not authenticated');
      return;
    }

    try {
      const client = clientRef.current!;
      wsRef.current = client.connectWebSocket(
        (message) => {
          setMessages((prev) => [...prev, message]);
        },
        (error) => {
          setError('WebSocket connection failed');
        }
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'WebSocket connection failed');
    }
  }, [isAuthenticated]);

  const disconnectWebSocket = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
  }, []);

  return {
    isAuthenticated,
    loading,
    error,
    messages,
    currentConversation,
    login,
    logout,
    sendMessage,
    loadConversation,
    markAsRead,
    connectWebSocket,
    disconnectWebSocket,
  };
}

// Component Example
export function ChatComponent() {
  const {
    isAuthenticated,
    loading,
    error,
    messages,
    login,
    logout,
    sendMessage,
    loadConversation,
    connectWebSocket,
  } = useChat4All();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [recipientId, setRecipientId] = useState('');
  const [messageContent, setMessageContent] = useState('');
  const [conversationId, setConversationId] = useState('');

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    await login(username, password);
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    await sendMessage(recipientId, messageContent, conversationId);
    setMessageContent('');
  };

  const handleLoadConversation = async (e: React.FormEvent) => {
    e.preventDefault();
    await loadConversation(conversationId);
  };

  if (!isAuthenticated) {
    return (
      <div className="login-form">
        <h2>Chat4All Login</h2>
        {error && <div className="error">{error}</div>}
        <form onSubmit={handleLogin}>
          <input
            type="text"
            placeholder="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <button type="submit" disabled={loading}>
            {loading ? 'Logging in...' : 'Login'}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="chat-app">
      <div className="chat-header">
        <h2>Chat4All</h2>
        <button onClick={logout}>Logout</button>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="conversation-loader">
        <form onSubmit={handleLoadConversation}>
          <input
            type="text"
            placeholder="Conversation ID"
            value={conversationId}
            onChange={(e) => setConversationId(e.target.value)}
          />
          <button type="submit">Load Conversation</button>
          <button type="button" onClick={connectWebSocket}>
            Connect WebSocket
          </button>
        </form>
      </div>

      <div className="messages">
        {loading ? (
          <p>Loading...</p>
        ) : (
          messages.map((msg) => (
            <div key={msg.messageId} className="message">
              <strong>{msg.senderName}:</strong> {msg.content}
              <small>{msg.status}</small>
            </div>
          ))
        )}
      </div>

      <form onSubmit={handleSendMessage} className="send-form">
        <input
          type="text"
          placeholder="Recipient ID"
          value={recipientId}
          onChange={(e) => setRecipientId(e.target.value)}
          required
        />
        <textarea
          placeholder="Message"
          value={messageContent}
          onChange={(e) => setMessageContent(e.target.value)}
          required
        />
        <button type="submit">Send</button>
      </form>
    </div>
  );
}
```

---

## Java (Spring Boot)

### Spring Boot Service Implementation

```java
// Chat4AllClientService.java
package com.chat4all.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class Chat4AllClientService {

  private final RestTemplate restTemplate;
  private final WebClient webClient;

  @Value("${chat4all.keycloak.url:http://localhost:8888}")
  private String keycloakUrl;

  @Value("${chat4all.keycloak.realm:chat4all}")
  private String keycloakRealm;

  @Value("${chat4all.client-id:chat4all-client}")
  private String clientId;

  @Value("${chat4all.message-service-url:http://localhost:8081}")
  private String messageServiceUrl;

  @Value("${chat4all.user-service-url:http://localhost:8083}")
  private String userServiceUrl;

  private String accessToken;

  /**
   * Authenticate with Keycloak
   */
  public void authenticate(String username, String password) {
    String tokenUrl = String.format(
      "%s/realms/%s/protocol/openid-connect/token",
      keycloakUrl,
      keycloakRealm
    );

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("grant_type", "password");
    requestBody.put("client_id", clientId);
    requestBody.put("username", username);
    requestBody.put("password", password);

    try {
      TokenResponse response = restTemplate.postForObject(
        tokenUrl,
        requestBody,
        TokenResponse.class
      );

      this.accessToken = response.getAccessToken();
      log.info("Successfully authenticated user: {}", username);
    } catch (Exception e) {
      log.error("Authentication failed", e);
      throw new RuntimeException("Authentication failed: " + e.getMessage());
    }
  }

  /**
   * Send a message
   */
  public MessageResponse sendMessage(
    String conversationId,
    String recipientId,
    String content
  ) {
    String url = messageServiceUrl + "/api/v1/messages";

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("conversationId", conversationId);
    requestBody.put("recipientId", recipientId);
    requestBody.put("content", content);
    requestBody.put("type", "TEXT");
    requestBody.put("channel", "WHATSAPP");

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

    try {
      MessageResponse response = webClient
        .post()
        .uri(url)
        .headers(httpHeaders -> httpHeaders.addAll(headers))
        .bodyValue(requestBody)
        .retrieve()
        .bodyToMono(MessageResponse.class)
        .block();

      log.info("Message sent: {}", response.getMessageId());
      return response;
    } catch (Exception e) {
      log.error("Failed to send message", e);
      throw new RuntimeException("Failed to send message: " + e.getMessage());
    }
  }

  /**
   * Get conversation messages
   */
  public ConversationResponse getConversationMessages(String conversationId) {
    String url = String.format(
      "%s/api/v1/conversations/%s/messages",
      messageServiceUrl,
      conversationId
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    try {
      ConversationResponse response = webClient
        .get()
        .uri(url)
        .headers(httpHeaders -> httpHeaders.addAll(headers))
        .retrieve()
        .bodyToMono(ConversationResponse.class)
        .block();

      log.info("Retrieved {} messages", response.getMessages().size());
      return response;
    } catch (Exception e) {
      log.error("Failed to fetch messages", e);
      throw new RuntimeException("Failed to fetch messages: " + e.getMessage());
    }
  }

  /**
   * Mark conversation as read
   */
  public void markAsRead(String conversationId) {
    String url = String.format(
      "%s/api/v1/conversations/%s/read",
      messageServiceUrl,
      conversationId
    );

    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("readAt", System.currentTimeMillis() + "");

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

    try {
      webClient
        .put()
        .uri(url)
        .headers(httpHeaders -> httpHeaders.addAll(headers))
        .bodyValue(requestBody)
        .retrieve()
        .bodyToMono(Void.class)
        .block();

      log.info("Marked conversation as read: {}", conversationId);
    } catch (Exception e) {
      log.error("Failed to mark as read", e);
    }
  }

  // Data classes
  static class TokenResponse {
    private String access_token;
    private String token_type;
    private int expires_in;

    public String getAccessToken() {
      return access_token;
    }
  }

  static class MessageResponse {
    private String messageId;
    private String conversationId;
    private String status;

    public String getMessageId() {
      return messageId;
    }
  }

  static class ConversationResponse {
    private java.util.List<MessageResponse> messages;

    public java.util.List<MessageResponse> getMessages() {
      return messages;
    }
  }
}
```

---

## Python

### Python SDK

```python
# chat4all_client.py
import requests
import json
from typing import Optional, Dict, List
from datetime import datetime

class Chat4AllClient:
    def __init__(
        self,
        keycloak_url: str = "http://localhost:8888",
        api_gateway_url: str = "http://localhost:8080",
        message_service_url: str = "http://localhost:8081",
        user_service_url: str = "http://localhost:8083",
        client_id: str = "chat4all-client"
    ):
        self.keycloak_url = keycloak_url
        self.api_gateway_url = api_gateway_url
        self.message_service_url = message_service_url
        self.user_service_url = user_service_url
        self.client_id = client_id
        
        self.auth_token: Optional[str] = None
        self.user_id: Optional[str] = None
        self.session = requests.Session()

    def login(self, username: str, password: str) -> None:
        """Authenticate user with username and password"""
        token_url = f"{self.keycloak_url}/realms/chat4all/protocol/openid-connect/token"
        
        payload = {
            "grant_type": "password",
            "client_id": self.client_id,
            "username": username,
            "password": password,
            "scope": "openid profile email"
        }
        
        try:
            response = requests.post(token_url, data=payload)
            response.raise_for_status()
            
            token_data = response.json()
            self.auth_token = token_data["access_token"]
            
            # Set authorization header for all requests
            self.session.headers.update({
                "Authorization": f"Bearer {self.auth_token}"
            })
            
            # Get user ID
            user_profile = self.session.get(
                f"{self.user_service_url}/api/v1/users/me"
            ).json()
            self.user_id = user_profile["userId"]
            
            print(f"Logged in as: {username}")
            
        except requests.exceptions.RequestException as e:
            print(f"Login failed: {e}")
            raise

    def send_message(
        self,
        conversation_id: str,
        recipient_id: str,
        content: str,
        channel: str = "WHATSAPP"
    ) -> Dict:
        """Send a message"""
        url = f"{self.message_service_url}/api/v1/messages"
        
        payload = {
            "conversationId": conversation_id,
            "recipientId": recipient_id,
            "content": content,
            "type": "TEXT",
            "channel": channel
        }
        
        try:
            response = self.session.post(url, json=payload)
            response.raise_for_status()
            
            message = response.json()
            print(f"Message sent: {message['messageId']}")
            return message
            
        except requests.exceptions.RequestException as e:
            print(f"Failed to send message: {e}")
            raise

    def get_conversation_messages(
        self,
        conversation_id: str,
        limit: int = 50,
        before: Optional[str] = None
    ) -> Dict:
        """Fetch conversation messages"""
        url = f"{self.message_service_url}/api/v1/conversations/{conversation_id}/messages"
        
        params = {"limit": limit}
        if before:
            params["before"] = before
        
        try:
            response = self.session.get(url, params=params)
            response.raise_for_status()
            
            conversation = response.json()
            print(f"Retrieved {len(conversation['messages'])} messages")
            return conversation
            
        except requests.exceptions.RequestException as e:
            print(f"Failed to fetch messages: {e}")
            raise

    def mark_as_read(self, conversation_id: str) -> None:
        """Mark conversation as read"""
        url = f"{self.message_service_url}/api/v1/conversations/{conversation_id}/read"
        
        payload = {
            "readAt": datetime.utcnow().isoformat() + "Z"
        }
        
        try:
            response = self.session.put(url, json=payload)
            response.raise_for_status()
            print(f"Marked {conversation_id} as read")
            
        except requests.exceptions.RequestException as e:
            print(f"Failed to mark as read: {e}")
            raise

    def get_message_status(self, message_id: str) -> Dict:
        """Get message status"""
        url = f"{self.message_service_url}/api/v1/messages/{message_id}/status"
        
        try:
            response = self.session.get(url)
            response.raise_for_status()
            
            status = response.json()
            print(f"Message status: {status['currentStatus']}")
            return status
            
        except requests.exceptions.RequestException as e:
            print(f"Failed to get status: {e}")
            raise

    def is_authenticated(self) -> bool:
        """Check if authenticated"""
        return self.auth_token is not None


# Usage Example
if __name__ == "__main__":
    client = Chat4AllClient()
    
    # Login
    client.login("alice", "alice123")
    
    # Send message
    message = client.send_message(
        conversation_id="conv-alice-bob-001",
        recipient_id="bob-user-id",
        content="Hello Bob!"
    )
    
    # Fetch messages
    conversation = client.get_conversation_messages("conv-alice-bob-001")
    for msg in conversation["messages"]:
        print(f"{msg['senderName']}: {msg['content']}")
    
    # Mark as read
    client.mark_as_read("conv-alice-bob-001")
    
    # Check status
    status = client.get_message_status(message["messageId"])
    print(f"Final status: {status['currentStatus']}")
```

---

## cURL (HTTP)

### Complete cURL Examples

```bash
#!/bin/bash

# Configuration
KEYCLOAK_URL="http://localhost:8888"
MESSAGE_SERVICE_URL="http://localhost:8081"
USER_SERVICE_URL="http://localhost:8083"
CLIENT_ID="chat4all-client"

# ============================================
# Step 1: Authenticate
# ============================================
echo "=== Step 1: Authenticate ==="

TOKEN_RESPONSE=$(curl -s -X POST \
  "$KEYCLOAK_URL/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=$CLIENT_ID" \
  -d "username=alice" \
  -d "password=alice123")

TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')
echo "Token: ${TOKEN:0:50}..."

# ============================================
# Step 2: Get User ID
# ============================================
echo -e "\n=== Step 2: Get User ID ==="

USER_PROFILE=$(curl -s "$USER_SERVICE_URL/api/v1/users/me" \
  -H "Authorization: Bearer $TOKEN")

USER_ID=$(echo $USER_PROFILE | jq -r '.userId')
echo "User ID: $USER_ID"

# ============================================
# Step 3: Send Message
# ============================================
echo -e "\n=== Step 3: Send Message ==="

MESSAGE=$(curl -s -X POST "$MESSAGE_SERVICE_URL/api/v1/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-test-001",
    "recipientId": "recipient-id",
    "content": "Hello from cURL!",
    "type": "TEXT",
    "channel": "WHATSAPP"
  }')

MESSAGE_ID=$(echo $MESSAGE | jq -r '.messageId')
echo "Message ID: $MESSAGE_ID"
echo "Status: $(echo $MESSAGE | jq -r '.status')"

# ============================================
# Step 4: Get Conversation
# ============================================
echo -e "\n=== Step 4: Get Conversation ==="

CONVERSATION=$(curl -s "$MESSAGE_SERVICE_URL/api/v1/conversations/conv-test-001/messages" \
  -H "Authorization: Bearer $TOKEN")

echo "Messages: $(echo $CONVERSATION | jq '.messages | length')"
echo $CONVERSATION | jq '.messages[] | {sender: .senderName, content: .content, status: .status}'

# ============================================
# Step 5: Mark as Read
# ============================================
echo -e "\n=== Step 5: Mark as Read ==="

curl -s -X PUT "$MESSAGE_SERVICE_URL/api/v1/conversations/conv-test-001/read" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"readAt": "'$(date -u +'%Y-%m-%dT%H:%M:%SZ')'"}'

echo "Marked as read"
```

---

## Postman Collection

### Import to Postman

```json
{
  "info": {
    "name": "Chat4All v2 API",
    "description": "Complete Chat4All authentication and messaging flow",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Authentication",
      "item": [
        {
          "name": "Login",
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "var jsonData = pm.response.json();",
                  "pm.environment.set(\"token\", jsonData.access_token);"
                ]
              }
            }
          ],
          "request": {
            "method": "POST",
            "header": [],
            "body": {
              "mode": "formdata",
              "formdata": [
                {"key": "grant_type", "value": "password"},
                {"key": "client_id", "value": "chat4all-client"},
                {"key": "username", "value": "alice"},
                {"key": "password", "value": "alice123"}
              ]
            },
            "url": {
              "raw": "{{keycloak_url}}/realms/chat4all/protocol/openid-connect/token",
              "host": ["{{keycloak_url}}"],
              "path": ["realms", "chat4all", "protocol", "openid-connect", "token"]
            }
          }
        }
      ]
    },
    {
      "name": "Messages",
      "item": [
        {
          "name": "Send Message",
          "request": {
            "auth": {
              "type": "bearer",
              "bearer": [{"key": "token", "value": "{{token}}", "type": "string"}]
            },
            "method": "POST",
            "header": [{"key": "Content-Type", "value": "application/json"}],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"conversationId\": \"conv-test-001\",\n  \"recipientId\": \"recipient-id\",\n  \"content\": \"Hello from Postman!\",\n  \"type\": \"TEXT\",\n  \"channel\": \"WHATSAPP\"\n}"
            },
            "url": {
              "raw": "{{message_service_url}}/api/v1/messages",
              "host": ["{{message_service_url}}"],
              "path": ["api", "v1", "messages"]
            }
          }
        },
        {
          "name": "Get Conversation",
          "request": {
            "auth": {
              "type": "bearer",
              "bearer": [{"key": "token", "value": "{{token}}", "type": "string"}]
            },
            "method": "GET",
            "url": {
              "raw": "{{message_service_url}}/api/v1/conversations/conv-test-001/messages",
              "host": ["{{message_service_url}}"],
              "path": ["api", "v1", "conversations", "conv-test-001", "messages"]
            }
          }
        }
      ]
    }
  ],
  "variable": [
    {"key": "keycloak_url", "value": "http://localhost:8888"},
    {"key": "message_service_url", "value": "http://localhost:8081"},
    {"key": "user_service_url", "value": "http://localhost:8083"},
    {"key": "token", "value": ""}
  ]
}
```

---

## Environment Variables

### .env Template

```bash
# Keycloak
KEYCLOAK_URL=http://localhost:8888
KEYCLOAK_REALM=chat4all
KEYCLOAK_CLIENT_ID=chat4all-client

# Services
API_GATEWAY_URL=http://localhost:8080
MESSAGE_SERVICE_URL=http://localhost:8081
USER_SERVICE_URL=http://localhost:8083
FILE_SERVICE_URL=http://localhost:8084

# Test Credentials
TEST_USER_1_USERNAME=alice
TEST_USER_1_PASSWORD=alice123
TEST_USER_2_USERNAME=bob
TEST_USER_2_PASSWORD=bob123

# Conversation
TEST_CONVERSATION_ID=conv-test-001
```

---

## Next Steps

1. **Choose your framework**: Select the implementation that matches your stack
2. **Install dependencies**: Follow framework-specific installation guides
3. **Configure endpoints**: Update URLs to match your deployment
4. **Test authentication**: Verify token generation works
5. **Implement error handling**: Add retry logic and fallbacks
6. **Monitor performance**: Track latency and error rates

See [AUTHENTICATION_AND_MESSAGING_FLOW.md](AUTHENTICATION_AND_MESSAGING_FLOW.md) for complete flow documentation.
