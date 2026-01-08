package com.imt.demo.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class UserDto {
    private String name;
    private String id;
    private boolean isAdmin;
}
