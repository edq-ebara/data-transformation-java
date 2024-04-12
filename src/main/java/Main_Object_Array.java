import java.util.ArrayList;
import java.util.List;

/**
 * 对象转换数组
 */
public class Main_Object_Array {

    public static void main(String[] args) {


        String orgJson = "{\"Name_Org\":\"JZM\",\"Age_Org\":\"18\"}"; // JSON字符串
        String aimJson = "[{\"Name\":\"11\",\"Value\":\"22\"}]"; // JSON字符串

        /// 1：源Key->目标Key
        /// 2：源Key->目标Value
        /// 3：源Value->目标Key
        /// 4：源Value->目标Value
        List<JsonMapping> jsonMappings = new ArrayList<>();
        jsonMappings.add(new JsonMapping("root", "root", 4));
        jsonMappings.add(new JsonMapping("root", "root.*", 4));
        jsonMappings.add(new JsonMapping("root[*].Value", "root.*", 4));


        JsonTranferUtil jsonTranferUtil = null;
        String tranJsonResult = null;
        try {
            jsonTranferUtil = new JsonTranferUtil(orgJson, aimJson, jsonMappings);
            tranJsonResult = jsonTranferUtil.tranJson();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("******************结果 **********************");

        System.out.println("The Result:" + tranJsonResult);


    }
}
