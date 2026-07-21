package com.smartstudy.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDeletedEvent {
    private String firebaseUid;
    private long timestamp;
}
