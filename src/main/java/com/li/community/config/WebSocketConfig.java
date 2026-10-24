package com.li.community.config;

import com.li.community.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils jwtUtils;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 允许跨域连接并启用 SockJS 回退
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 广播主题: /topic, 点对点定向队列: /queue
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    Long userId = authenticate(accessor);
                    if (userId == null) {
                        throw new MessagingException("WebSocket 连接缺少有效令牌，已拒绝");
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    // 私信会话主题包含会话双方 ID，只允许会话成员订阅，防止旁听
                    if (destination != null && destination.startsWith("/topic/chat/")) {
                        Long userId = currentUserId(accessor);
                        if (userId == null || !isChatSessionMember(destination, userId)) {
                            log.warn("拒绝非会话成员订阅私信主题: destination={}, userId={}", destination, userId);
                            throw new MessagingException("无权订阅该私信会话");
                        }
                    }
                }
                return message;
            }

            private Long authenticate(StompHeaderAccessor accessor) {
                List<String> authorization = accessor.getNativeHeader("Authorization");
                if (authorization == null || authorization.isEmpty()) {
                    return null;
                }
                String authHeader = authorization.get(0);
                if (!authHeader.startsWith("Bearer ")) {
                    return null;
                }
                String token = authHeader.substring(7);
                if (jwtUtils.isTokenExpired(token)) {
                    return null;
                }
                Long userId = jwtUtils.getUserId(token);
                String username = jwtUtils.getUsername(token);
                String role = jwtUtils.getRole(token);
                if (userId == null || role == null) {
                    return null;
                }
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userId, null,
                                Collections.singletonList(new SimpleGrantedAuthority(role))
                        );
                accessor.setUser(auth);
                log.info("WebSocket STOMP 客户端连接鉴权成功: userId={}, username={}", userId, username);
                return userId;
            }

            private Long currentUserId(StompHeaderAccessor accessor) {
                if (accessor.getUser() instanceof Authentication authentication
                        && authentication.getPrincipal() instanceof Long userId) {
                    return userId;
                }
                return null;
            }

            /**
             * 会话主题格式: /topic/chat/post_{postId}_{minUid}_{maxUid}，
             * 末两段为会话双方用户 ID
             */
            private boolean isChatSessionMember(String destination, Long userId) {
                String[] parts = destination.split("_");
                if (parts.length < 4) {
                    return false;
                }
                String uid = String.valueOf(userId);
                return uid.equals(parts[parts.length - 1]) || uid.equals(parts[parts.length - 2]);
            }
        });
    }
}
