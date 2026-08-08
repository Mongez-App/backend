package com.smartstudy.identity.service.impl;

import com.smartstudy.identity.dto.UserMapper;
import com.smartstudy.identity.dto.request.UpdateCalendarSyncRequest;
import com.smartstudy.identity.dto.response.CalendarSyncResponse;
import com.smartstudy.identity.model.User;
import com.smartstudy.identity.repository.UserRepository;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private final String uid = "test-uid-123";

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id(uid)
                .email("test@example.com")
                .calendarConnected(false)
                .calendarSynced(false)
                .build();
    }

    // --- getCalendarSync ---

    @Test
    void testGetCalendarSync_existingUser_returnsFlagsAndTimestamp() {
        Instant syncTime = Instant.parse("2025-01-15T10:30:00Z");
        existingUser.setLastCalendarSyncAt(syncTime);
        when(userRepository.findById(uid)).thenReturn(Optional.of(existingUser));

        CalendarSyncResponse response = authService.getCalendarSync(uid);

        assertNotNull(response);
        assertFalse(response.calendarConnected());
        assertFalse(response.calendarSynced());
        assertEquals(syncTime, response.lastCalendarSyncAt());
    }

    @Test
    void testGetCalendarSync_userNotFound_throwsNotFoundException() {
        when(userRepository.findById(uid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authService.getCalendarSync(uid));
    }

    // --- updateCalendarSync ---

    @Test
    void testUpdateCalendarSync_bothFlagsSet_setsLastSyncAt() {
        when(userRepository.findById(uid)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCalendarSyncRequest request = new UpdateCalendarSyncRequest(true, true);

        CalendarSyncResponse response = authService.updateCalendarSync(uid, request);

        assertNotNull(response);
        assertTrue(response.calendarConnected());
        assertTrue(response.calendarSynced());
        assertNotNull(response.lastCalendarSyncAt());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertTrue(savedUser.isCalendarConnected());
        assertTrue(savedUser.isCalendarSynced());
        assertNotNull(savedUser.getLastCalendarSyncAt());
    }

    @Test
    void testUpdateCalendarSync_partialUpdate_onlyAppliesNonNullFields() {
        existingUser.setCalendarConnected(true);
        existingUser.setCalendarSynced(true);
        Instant originalSyncTime = Instant.parse("2025-01-15T10:30:00Z");
        existingUser.setLastCalendarSyncAt(originalSyncTime);
        when(userRepository.findById(uid)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCalendarSyncRequest request = new UpdateCalendarSyncRequest(null, false);

        CalendarSyncResponse response = authService.updateCalendarSync(uid, request);

        assertTrue(response.calendarConnected());
        assertFalse(response.calendarSynced());
        assertEquals(originalSyncTime, response.lastCalendarSyncAt());

        verify(userRepository).save(existingUser);
    }

    @Test
    void testUpdateCalendarSync_calendarSyncedFalse_doesNotUpdateLastSyncAt() {
        Instant originalSyncTime = Instant.parse("2025-01-15T10:30:00Z");
        existingUser.setLastCalendarSyncAt(originalSyncTime);
        when(userRepository.findById(uid)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCalendarSyncRequest request = new UpdateCalendarSyncRequest(true, false);

        CalendarSyncResponse response = authService.updateCalendarSync(uid, request);

        assertTrue(response.calendarConnected());
        assertFalse(response.calendarSynced());
        assertEquals(originalSyncTime, response.lastCalendarSyncAt());
    }

    @Test
    void testUpdateCalendarSync_calendarConnectedOnly_doesNotSetLastSyncAt() {
        existingUser.setLastCalendarSyncAt(null);
        when(userRepository.findById(uid)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCalendarSyncRequest request = new UpdateCalendarSyncRequest(true, null);

        CalendarSyncResponse response = authService.updateCalendarSync(uid, request);

        assertTrue(response.calendarConnected());
        assertFalse(response.calendarSynced());
        assertNull(response.lastCalendarSyncAt());
    }

    @Test
    void testUpdateCalendarSync_noFields_throwsBadRequestException() {
        when(userRepository.findById(uid)).thenReturn(Optional.of(existingUser));

        UpdateCalendarSyncRequest request = new UpdateCalendarSyncRequest(null, null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.updateCalendarSync(uid, request));
        assertEquals("INVALID_REQUEST", ex.getErrorCode());
    }

    @Test
    void testUpdateCalendarSync_userNotFound_throwsNotFoundException() {
        when(userRepository.findById(uid)).thenReturn(Optional.empty());

        UpdateCalendarSyncRequest request = new UpdateCalendarSyncRequest(true, true);

        assertThrows(NotFoundException.class,
                () -> authService.updateCalendarSync(uid, request));
        verify(userRepository, never()).save(any(User.class));
    }
}
