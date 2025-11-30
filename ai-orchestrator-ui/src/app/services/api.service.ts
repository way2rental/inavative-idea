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

  chatStream(request: ChatRequest): Observable<string> {
    const subject = new Subject<string>();

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

    // Try streaming endpoint first, fall back to regular if not available
    fetch(`${this.baseUrl}/v2/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
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

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        subject.next(chunk);
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
