package my.test01.util;

/**
 * Created by Howard Yao on 2018/7/29.
 */
public class Permission {
    private String desc;

    @Override
    public String toString() {
        return "Permission{" +
                "desc='" + desc + '\'' +
                '}';
    }


    public Permission() {

    }

    public Permission(String desc) {

        this.desc = desc;
    }
}
