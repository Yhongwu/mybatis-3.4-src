package my.test01.util;

import my.test01.util.Role;

import java.util.List;

/**
 * Created by Howard Yao on 2018/7/28.
 */
public class User {
    private String name;
    private List<Role> roles;


    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", roles=" + roles +
                '}';
    }

    public User(String name, List<Role> roles) {
        this.name = name;
        this.roles = roles;
    }

    public String getName() {

        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }



    public User() {

    }


}
