package com.smartstudy.planning.exception;

/**
 * identity-service could not be reached to resolve an organization's name.
 *
 * Deliberately fatal rather than degrading to null, unlike the other
 * identity-service fallbacks. {@code organization_name} is a denormalized copy
 * written once when a team is created and never revisited, so a null persisted
 * here is permanent and silent: the team drops out of organization-name search
 * (see TeamService, which skips teams whose name is null) and passes the null
 * on to every course created under it. A failed create the admin can retry is
 * the cheaper outcome.
 */
public class OrganizationLookupUnavailableException extends RuntimeException {

    public OrganizationLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
