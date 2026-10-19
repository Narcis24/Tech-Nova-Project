package com.neueda.app.dtos;

import com.neueda.app.enums.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The complete set of roles a user should have from now on; replaces what they had. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolesRequest {
    // A Set, so ["TRADER", "TRADER"] is one role; an unknown name fails to parse (400 MALFORMED_REQUEST)
    @NotEmpty(message = "A user needs at least one role")
    private Set<@NotNull Role> roles;
}
