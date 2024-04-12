/**
 * Json映射选项
 */
public class JsonMappingOptions {


//    1.  变量   #Time#  #Time_L#
//    2.  Mapping信息
//    {
//        "AimJsonPath": "root.dev.642fccd1",//目标结构路径
//            "OrgJsonPath": "root.dev[*].pro",//源结构路径
//            "TranType": 4,//转换类型  1：源Key->目标Key  2：源Key->目标Value  3：源Value->目标Key 4：源Value->目标Value
//            "Options":{
//                "KeyInitIndex":100,//自动生成的元素的起始索引,默认为0
//                "AddElementsOption":"1",  //1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
//                "TranOP":"1",  //1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key 3：依据源元素在目标构建同等数量的目标子元素 默认为1
//                "TranWay":"1", //1：交叉映射 2：一对一映射 默认为1
//           }
//    },


    /**
     * 自动生成的元素的起始索引,默认为0
     */
    private int keyInitIndex=0;

    /**
     * 1:追加新元素到数组/对象 2：替换数组/对象的原有属性  默认为1
     */
    private int addElementsOption=1;

    /**
     * 1:将源子元素复制到目标,使用源子元素的Key 2:将源子元素复制到目标,为源子元素新生成Key 3：依据源元素在目标构建同等数量的目标子元素 默认为1
     */
    private int tranOP=1;

    /**
     * 1：交叉映射 2：一对一映射 默认为1
     */
    private int tranWay=1;


    public int getKeyInitIndex() {
        return keyInitIndex;
    }

    public void setKeyInitIndex(int keyInitIndex) {
        this.keyInitIndex = keyInitIndex;
    }

    public int getAddElementsOption() {
        return this.addElementsOption;
    }

    public void setAddElementsOption(int addElementsOption) {
        this.addElementsOption = addElementsOption;
    }

    public int getTranOP() {
        return   this.tranOP;
    }

    public void setTranOP(int tranOP) {
        this.tranOP = tranOP;
    }

    public int getTranWay() {
        return this.tranWay;
    }

    public void setTranWay(int tranWay) {
        this.tranWay = tranWay;
    }


    public JsonMappingOptions() {

    }

    public JsonMappingOptions(int keyInitIndex, int addElementsOption, int tranOP, int tranWay) {
        this.keyInitIndex = keyInitIndex;
        this.addElementsOption = addElementsOption;
        this.tranOP = tranOP;
        this.tranWay = tranWay;
    }
}