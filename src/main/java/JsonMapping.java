/**
 * Json映射
 */
public class JsonMapping {

    /// <summary>
    /// 要转换的目标Json路径
    /// </summary>
    public String aimJsonPath;

    /// <summary>
    ///要转换的源Json路径
    /// </summary>
    public String orgJsonPath;

    /// 转换类型
    /// 1：源Key->目标Key
    /// 2：源Key->目标Value
    /// 3：源Value->目标Key
    /// 4：源Value->目标Value
    public int tranType;


    public String getAimJsonPath() {
        return aimJsonPath;
    }

    public void setAimJsonPath(String aimJsonPath) {
        this.aimJsonPath = aimJsonPath;
    }


    public String getOrgJsonPath() {
        return orgJsonPath;
    }

    public void setOrgJsonPath(String orgJsonPath) {
        this.orgJsonPath = orgJsonPath;
    }

    public int getTranType() {
        return tranType;
    }

    public void setTranType(int tranType) {
        this.tranType = tranType;
    }


    public JsonMapping() {

    }

    public JsonMapping(String _aimJsonPath, String _orgJsonPath, int _tranType) {
        setAimJsonPath(_aimJsonPath);
        setOrgJsonPath(_orgJsonPath);
        setTranType(_tranType);
    }

}