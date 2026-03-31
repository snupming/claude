package com.ownpic.image;

import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.image.domain.*;
import com.ownpic.image.dto.PlatformConnectionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock PlatformConnectionRepository connectionRepository;
    @Mock ImageRepository imageRepository;
    @Mock UserRepository userRepository;

    @InjectMocks StoreService service;

    UUID userId;
    User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setName("테스트");
        user.setEmail("test@test.com");
        user.setPassword("encoded");
    }

    @Test
    void connect_newPlatform_savesAndReturnsResponse() {
        when(connectionRepository.existsByUserIdAndPlatform(userId, PlatformType.NAVER)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformConnectionResponse resp = service.connect(userId, "NAVER");

        assertThat(resp.platform()).isEqualTo("NAVER");
        assertThat(resp.status()).isEqualTo("CONNECTED");
        verify(connectionRepository, times(2)).save(any());
    }

    @Test
    void connect_duplicatePlatform_throwsConflict() {
        when(connectionRepository.existsByUserIdAndPlatform(userId, PlatformType.NAVER)).thenReturn(true);

        assertThatThrownBy(() -> service.connect(userId, "NAVER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 연동");
    }

    @Test
    void connect_invalidPlatform_throwsBadRequest() {
        assertThatThrownBy(() -> service.connect(userId, "INVALID_PLATFORM"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("지원하지 않는");
    }

    @Test
    void connect_userNotFound_throwsNotFound() {
        when(connectionRepository.existsByUserIdAndPlatform(userId, PlatformType.NAVER)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.connect(userId, "NAVER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("사용자");
    }

    @Test
    void getConnections_returnsListOfResponses() {
        var conn = new PlatformConnection();
        conn.setPlatform(PlatformType.COUPANG);
        conn.setStatus(ConnectionStatus.CONNECTED);
        conn.setUser(user);
        when(connectionRepository.findByUserId(userId)).thenReturn(List.of(conn));

        List<PlatformConnectionResponse> result = service.getConnections(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).platform()).isEqualTo("COUPANG");
    }

    @Test
    void disconnect_ownConnection_setsDisconnected() {
        var conn = new PlatformConnection();
        conn.setUser(user);
        conn.setStatus(ConnectionStatus.CONNECTED);
        // User.getId()가 null이므로 mock이 필요
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(userId);
        conn.setUser(mockUser);
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(conn));

        service.disconnect(userId, 1L);

        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        verify(connectionRepository).save(conn);
    }

    @Test
    void disconnect_otherUsersConnection_throwsNotFound() {
        var conn = new PlatformConnection();
        User otherUser = mock(User.class);
        when(otherUser.getId()).thenReturn(UUID.randomUUID());
        conn.setUser(otherUser);
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(conn));

        assertThatThrownBy(() -> service.disconnect(userId, 1L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void syncProducts_validConnection_updatesSyncStatus() {
        var conn = new PlatformConnection();
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(userId);
        conn.setUser(mockUser);
        conn.setStatus(ConnectionStatus.CONNECTED);
        conn.setPlatform(PlatformType.NAVER);
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(conn));

        service.syncProducts(userId, 1L);

        assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(conn.getLastSyncedAt()).isNotNull();
        verify(connectionRepository, times(2)).save(conn);
    }
}
