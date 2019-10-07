package my; /**
 *    Copyright ${license.git.copyrightYears} the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import my.test01.util.User;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.junit.Test;

import java.text.DecimalFormat;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Created by Howard Yao on 2018/7/28.
 */
public class myTest {
    public static void main(String[] args) {
        String name = "StudentName";
        System.out.println(Character.isUpperCase(name.charAt(1)));
        System.out.println(PropertyNamer.methodToProperty("isSTudentName"));
    }

    @Test
    public void testRandom() {
        Random rand = new Random();
        int nextInt = 0;
        /*for (int i = 0 ; i < 100; i ++ ) {
            System.out.println(rand.nextInt(100));
        }*/
        DecimalFormat df = new DecimalFormat("#.00");
        //System.out.println(df.format(f));
        for (int i = 0 ; i < 100; i ++ ) {
            double v = 0;
            int i1 = rand.nextInt(100);
            if (i1 < 30) {
                v = rand.nextDouble() * (rand.nextInt(100));
            }else if (i1 >= 30 && i1 <= 90){
                v = rand.nextDouble() * (rand.nextInt(10000));
            }else {
                v = rand.nextDouble() * (rand.nextInt(1000000));
            }
            //double v = rand.nextDouble() * (rand.nextInt(10000));
            while (v < 1) {
                v = v * 10;
            }
            System.out.println(df.format(v));
        }
    }
}
