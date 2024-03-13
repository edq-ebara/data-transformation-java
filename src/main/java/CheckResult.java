import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 检验结果
 */
public class CheckResult {



    private JsonMapping mapping;
    private String aimMsg;
    private String orgMsg;

    public JsonMapping getMapping() {
        return mapping;
    }

    public void setMapping(JsonMapping mapping) {
        this.mapping = mapping;
    }

    public String getAimMsg() {
        return aimMsg;
    }

    public void setAimMsg(String aimMsg) {
        this.aimMsg = aimMsg;
    }

    public String getOrgMsg() {
        return orgMsg;
    }

    public void setOrgMsg(String orgMsg) {
        this.orgMsg = orgMsg;
    }

    public CheckResult() {


    }

    public CheckResult(JsonMapping mapping) {
        this.mapping = mapping;
    }
}
