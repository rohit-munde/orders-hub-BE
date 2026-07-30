package com.indiedev.orders_hub.user;

import com.indiedev.orders_hub.common.BaseEnity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

//@Entity(name = "users")
public class User extends BaseEnity {

    private long id;

    private String email;

    private String name;

    private long google_id;

//    @Column(name = "profile_url", length = 2048)
    private String profileUrl;
}
