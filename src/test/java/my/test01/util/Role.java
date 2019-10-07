package my.test01.util;

/**
 * Created by Howard Yao on 2018/7/28.
 */
public class Role {
    private Integer code;
    private String name;
    private Double tt;
    private Permission p;

    public Role(Integer code, String name, Permission p) {
        this.code = code;
        this.name = name;
        this.p = p;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Role() {
    }

    public Role(Integer code, String name) {

        this.code = code;
        this.name = name;
    }

    @Override
    public String toString() {
        return "Role{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
