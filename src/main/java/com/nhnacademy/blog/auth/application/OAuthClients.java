package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.SocialProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 제공사별 OAuthClient. 앱 키가 없는 제공사는 빈 값이다. */
@Component
public class OAuthClients {

    private final Map<SocialProvider, OAuthClient> clients = new EnumMap<>(SocialProvider.class);

    public OAuthClients(List<OAuthClient> clients) {
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    public Optional<OAuthClient> configured(SocialProvider provider) {
        return Optional.ofNullable(clients.get(provider)).filter(OAuthClient::configured);
    }

}
