package my;

import java.util.*;

/**
 * @Author: yaohongwu
 * @Date: 2019/4/27 16:14
 * @Description:
 */
public class MapTest {
    public static void main(String[] args) {
//        HashMap<String, String> linkedHashMap = new LinkedHashMap<>();
//        linkedHashMap.put("a","1");
//        linkedHashMap.put("b","1");
//        linkedHashMap.put("c","1");
//        Set<Map.Entry<String, String>> mapSet = linkedHashMap.entrySet();
//        for (Map.Entry<String, String> entry : mapSet) {
//            String key = entry.getKey();
//            String value = entry.getValue();
//            System.out.println("key: " + key + "  value:" + value);
//        }


//        Map<String, String> linkedHashMap = new LinkedHashMap<>();
//        linkedHashMap.put("a","1");
//        linkedHashMap.put("b","1");
//        linkedHashMap.put("c","1");
//        Set<Map.Entry<String, String>> set = linkedHashMap.entrySet();
//        Iterator<Map.Entry<String, String>> iterator = set.iterator();
//        while(iterator.hasNext()) {
//            Map.Entry entry = iterator.next();
//            String key = (String) entry.getKey();
//            String value = (String) entry.getValue();
//            System.out.println(key + " " + value);
//        }
        int i = 14;
        System.out.println(i >> 1);
    }

    public static int highestOneBit(int i) {
        // HD, Figure 3-1
        i |= (i >>  1);
        i |= (i >>  2);
        i |= (i >>  4);
        i |= (i >>  8);
        i |= (i >> 16);
        return i - (i >>> 1);
    }
}
