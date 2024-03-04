import java.util.ArrayList;
import java.util.List;

/**
 * 对象转换对象
 */
public class Main_Array_Array {

    public static void main(String[] args) {

        String orgJson = "[{\"Name\":\"11\",\"Value\":\"22\"},{\"Name\":\"aa\",\"Value\":\"bb\"}]"; // JSON字符串
        String aimJson = "[{\"Name_Aim\":\"a1\",\"Value_Aim\":\"b1\"}]"; // JSON字符串


        /// 1：源Key->目标Key
        /// 2：源Key->目标Value
        /// 3：源Value->目标Key
        /// 4：源Value->目标Value
        List<JsonMapping> jsonMappings = new ArrayList<>();
        jsonMappings.add(new JsonMapping("root", "root", 4));
        jsonMappings.add(new JsonMapping("root[*].Name_Aim", "root[*].Name", 3));
        jsonMappings.add(new JsonMapping("root[*].Name_Aim", "root[*].Name", 4));
//        jsonMappings.add(new JsonMapping("root.Name_Org", "root[*].Value", 4));


        String resultStr = new JsonTranferUtils().tranJson(orgJson, aimJson, jsonMappings);

        System.out.println("******************结果 **********************");

        System.out.println("The Result:" + resultStr);




    }
}
