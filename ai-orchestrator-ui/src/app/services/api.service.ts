import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of, Subject } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ChatRequest, ChatResponse } from '../models/chat.model';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  chat(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.baseUrl}/chat`, request);
  }

  chatV2(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.baseUrl}/v2/chat`, request);
  }

  chatStream(request: ChatRequest): Observable<{ event: string; data: string }> {
    const subject = new Subject<{ event: string; data: string }>();

    // Get token from localStorage
    let token = '';
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      try {
        const user = JSON.parse(storedUser);
        token = user.token || '';
      } catch (e) {
        console.error('Failed to parse currentUser from localStorage:', e);
      }
    }

    if (!token) {
      subject.error(new Error('No authentication token available'));
      return subject.asObservable();
    }

    // CHUNK 3 SSE STABILITY: Use proper event handling
    fetch(`${this.baseUrl}/v2/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify(request)
    }).then(async response => {
      if (!response.ok) {
        const errorText = await response.text();
        console.error('Streaming request failed:', response.status, errorText);
        subject.error(new Error(`Streaming failed: ${response.status} - ${response.statusText}`));
        return;
      }

      if (!response.body) {
        subject.error(new Error('Streaming not available - no response body'));
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        // Decode chunk and add to buffer
        buffer += decoder.decode(value, { stream: true });

        // Process complete SSE messages in buffer
        // SSE format: event: <type>\ndata: <content>\n\n
        const messages = buffer.split('\n\n');

        // Keep last incomplete message in buffer
        buffer = messages.pop() || '';

        for (const message of messages) {
          if (!message.trim()) continue;

          const lines = message.split('\n');
          let eventType = 'message'; // Default event type
          let dataLines: string[] = []; // Collect all data lines for multi-line payloads

          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventType = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              // Handle both "data: " (with space) and "data:" (without space)
              const dataContent = line.startsWith('data: ')
                ? line.substring(6)
                : line.substring(5);
              dataLines.push(dataContent);
            }
          }

          // Join all data lines (handles multi-line JSON)
          const eventData = dataLines.join('\n');

          // Emit parsed SSE event
          subject.next({ event: eventType, data: eventData });

          // CHUNK 3: Mark stream for completion on 'done' event
          // Note: We don't return immediately to ensure all messages in buffer are processed
          if (eventType === 'done') {
            // Process remaining buffer before completing
            if (buffer.trim()) {
              const remainingLines = buffer.split('\n');
              let remainingEventType = 'message';
              let remainingEventData = '';

              for (const line of remainingLines) {
                if (line.startsWith('event:')) {
                  remainingEventType = line.substring(6).trim();
                } else if (line.startsWith('data:')) {
                  remainingEventData = line.startsWith('data: ')
                    ? line.substring(6)
                    : line.substring(5);
                }
              }

              if (remainingEventData || remainingEventType !== 'message') {
                subject.next({ event: remainingEventType, data: remainingEventData });
              }
            }
            subject.complete();
            return;
          }
        }
      }

      // Process any remaining data in buffer (if stream ended without 'done' event)
      if (buffer.trim()) {
        const lines = buffer.split('\n');
        let eventType = 'message';
        let dataLines: string[] = [];

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            const dataContent = line.startsWith('data: ')
              ? line.substring(6)
              : line.substring(5);
            dataLines.push(dataContent);
          }
        }

        const eventData = dataLines.join('\n');
        if (eventData || eventType !== 'message') {
          subject.next({ event: eventType, data: eventData });
        }
      }

      subject.complete();
    }).catch(err => {
      console.error('Streaming error:', err);
      subject.error(err);
    });

    return subject.asObservable();
  }

  getHealth(): Observable<any> {
    return this.http.get(`${this.baseUrl}/public/health`);
  }

  getInfo(): Observable<any> {
    return this.http.get(`${this.baseUrl}/public/info`);
  }

  getOllamaHealth(): Observable<any> {
    return this.http.get(`${this.baseUrl}/ollama/health`).pipe(
      catchError(() => of({ connected: false, enabled: true }))
    );
  }

  getOllamaModels(): Observable<any> {
    return this.http.get(`${this.baseUrl}/ollama/models`).pipe(
      catchError(() => of([]))
    );
  }
}
