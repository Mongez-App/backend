package com.smartstudy.identity.dto.request;

/**
 * Body of {@code POST /organization/updateProfile}. Both fields are optional
 * individually, but at least one must be present; an omitted field leaves the
 * stored value alone.
 */
public record OrgProfileUpdateRequest(
        String name,
        String photoUrl
) {
}
