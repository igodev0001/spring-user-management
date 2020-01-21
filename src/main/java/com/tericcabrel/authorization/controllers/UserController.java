package com.tericcabrel.authorization.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.IOException;

import org.hibernate.validator.constraints.Length;

import com.tericcabrel.authorization.dtos.UpdatePasswordDto;
import com.tericcabrel.authorization.dtos.UpdateUserDto;
import com.tericcabrel.authorization.models.User;
import com.tericcabrel.authorization.models.common.ServiceResponse;
import com.tericcabrel.authorization.exceptions.PasswordNotMatchException;
import com.tericcabrel.authorization.services.FileStorageService;
import com.tericcabrel.authorization.services.interfaces.IUserService;

@Api(tags = "User management", description = "Operations pertaining to user's update, fetch and delete")
@RestController
@RequestMapping(value = "/users")
@Validated
public class UserController {
    private IUserService userService;

    private FileStorageService fileStorageService;

    public UserController(IUserService userService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @ApiOperation(value = "Get all users", response = ServiceResponse.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "List retrieved successfully!"),
        @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
        @ApiResponse(code = 403, message = "You don't have the right to access to this resource"),
    })
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public ResponseEntity<ServiceResponse> all(){
        return ResponseEntity.ok(
                new ServiceResponse(HttpStatus.OK.value(), userService.findAll())
        );
    }

    @ApiOperation(value = "Get the authenticated user", response = ServiceResponse.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "User retrieved successfully!"),
        @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ServiceResponse> currentUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return ResponseEntity.ok(
                new ServiceResponse(HttpStatus.OK.value(), userService.findByEmail(authentication.getName()))
        );
    }

    @ApiOperation(value = "Get one user", response = ServiceResponse.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Item retrieved successfully!"),
        @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
        @ApiResponse(code = 403, message = "You don't have the right to access to this resource"),
    })
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_USER')")
    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> one(@PathVariable String id){
        return ResponseEntity.ok(
            new ServiceResponse(HttpStatus.OK.value(), userService.findById(id))
        );
    }

    @ApiOperation(value = "Update an user", response = ServiceResponse.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "User updated successfully!"),
        @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
        @ApiResponse(code = 403, message = "You don't have the right to access to this resource"),
        @ApiResponse(code = 422, message = "One or many parameters in the request's body are invalid"),
    })
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_USER')")
    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> update(@PathVariable String id, @RequestBody UpdateUserDto updateUserDto) {
        return ResponseEntity.ok(
            new ServiceResponse(HttpStatus.OK.value(), userService.update(id, updateUserDto))
        );
    }

    @ApiOperation(value = "Update user password", response = ServiceResponse.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "The password updated successfully!"),
        @ApiResponse(code = 400, message = "The current password is invalid"),
        @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
        @ApiResponse(code = 403, message = "You don't have the right to access to this resource"),
        @ApiResponse(code = 422, message = "One or many parameters in the request's body are invalid"),
    })
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_USER')")
    @PutMapping("/{id}/password")
    public ResponseEntity<ServiceResponse> updatePassword(
            @PathVariable String id, @Valid @RequestBody UpdatePasswordDto updatePasswordDto
    ) throws PasswordNotMatchException {
        User user = userService.updatePassword(id, updatePasswordDto);

        if (user == null) {
            throw new PasswordNotMatchException("The current password don't match!");
        }

        return ResponseEntity.ok(new ServiceResponse(HttpStatus.OK.value(), user));
    }

    @ApiOperation(value = "Delete an user", response = ServiceResponse.class)
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "User deleted successfully!"),
        @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
        @ApiResponse(code = 403, message = "You don't have the right to access to this resource"),
    })
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable String id) {
        userService.delete(id);

        return ResponseEntity.noContent().build();
    }

    @ApiOperation(value = "Change or delete user picture", response = ServiceResponse.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "The picture updated/deleted successfully!"),
        @ApiResponse(code = 400, message = "An IOException occurred!"),
        @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
        @ApiResponse(code = 403, message = "You don't have the right to access to this resource"),
        @ApiResponse(code = 422, message = "One or many parameters in the request's body are invalid"),
    })
    @PostMapping("/{id}/picture")
    public ResponseEntity<ServiceResponse> uploadPicture(
        @PathVariable String id,
        @RequestParam(name = "file", required = false) MultipartFile file,
        @RequestParam("action")
        @Pattern(regexp = "[ud]", message = "The valid value can be \"u\" or \"d\"")
        @Length(max = 1, message = "This field length can\'t be greater than 1")
        @NotBlank(message = "This field is required")
                    String action
    ) throws IOException {
        User user = null;
        UpdateUserDto updateUserDto = new UpdateUserDto();

        if (action.equals("u")) {
            String fileName = fileStorageService.storeFile(file);

            updateUserDto.setAvatar(fileName);

            user = userService.update(id, updateUserDto);
        } else if (action.equals("d")) {
            user = userService.findById(id);

            if (user.getAvatar() != null) {
                Resource resource = fileStorageService.loadFileAsResource(user.getAvatar());

                boolean deleted = resource.getFile().delete();

                if (deleted) {
                    user.setAvatar(null);
                    userService.update(user);
                }
            }
        } else {
            System.out.println("Unknown action!");
        }

        return ResponseEntity.ok().body(new ServiceResponse(HttpStatus.OK.value(), user));
    }
}