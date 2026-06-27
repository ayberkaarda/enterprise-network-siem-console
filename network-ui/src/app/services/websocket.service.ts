import { Injectable } from '@angular/core';
import { Client, Message } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private client: Client;
  private alertSubject = new Subject<any>();
  public alerts$ = this.alertSubject.asObservable();

  constructor() {
    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws-siem'),
      debug: (msg: string) => console.log(msg),
      reconnectDelay: 5000,
    });

    this.client.onConnect = (frame) => {
      console.log('WebSocket Bağlandı: ' + frame);
      this.client.subscribe('/topic/alerts', (message: Message) => {
        if (message.body) {
          this.alertSubject.next(JSON.parse(message.body));
        }
      });
    };

    this.client.activate();
  }
}
