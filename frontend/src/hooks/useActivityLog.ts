import { Client } from '@stomp/stompjs'
import { useEffect, useState } from 'react'

export type ActivityEvent = {
  type: string
  message: string
  timestamp: string
}

export function useActivityLog() {
  const [events, setEvents] = useState<ActivityEvent[]>([])

  useEffect(() => {
    const client = new Client({
      brokerURL: `ws://${window.location.host}/ws/websocket`,
      webSocketFactory: () => {
        // SockJS fallback for nginx proxy
        const SockJS = (window as any).SockJS
        if (SockJS) return new SockJS('/ws')
        return new WebSocket(`ws://${window.location.host}/ws/websocket`)
      },
      onConnect: () => {
        client.subscribe('/topic/activity', (msg) => {
          const event: ActivityEvent = JSON.parse(msg.body)
          setEvents((prev) => [event, ...prev].slice(0, 1000))
        })
      },
      onStompError: (frame) => {
        console.warn('STOMP error', frame)
      },
      reconnectDelay: 5000,
    })
    client.activate()
    return () => {
      client.deactivate()
    }
  }, [])

  return events
}
