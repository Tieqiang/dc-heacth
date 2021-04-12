package com.dchealth.service.rare;

import com.dchealth.VO.*;
import com.dchealth.entity.common.YunDictitem;
import com.dchealth.entity.common.YunUsers;
import com.dchealth.entity.rare.*;
import com.dchealth.facade.common.BaseFacade;
import com.dchealth.util.JSONUtil;
import com.dchealth.util.StringUtils;
import com.dchealth.util.UserUtils;
import org.codehaus.jettison.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.util.*;

/**
 * Created by Administrator on 2017/6/21.
 */
@Controller
@Produces("application/json")
@Path("template")
public class TemplateService {

    @Autowired
    private BaseFacade baseFacade ;


    /**
     * 获取工作流列表
     * @param dcode
     * @param doctorId
     * @param title
     * @param mblx
     * @return
     */
    @GET
    @Path("list")
    public List<YunDisTemplet> getDisTemplate(@QueryParam("dcode") String dcode,@QueryParam("doctorId") String doctorId,
                                              @QueryParam("title")String title,@QueryParam("mblx")String mblx,
                                              @QueryParam("deptId")String deptId,
                                              @QueryParam("pubFlag") String pubFlag) throws Exception {
        String hql = "from YunDisTemplet as t where 1=1 and mblx<>'WORK'" ;
        //所属疾病
        if(!"".equals(dcode)&&dcode!=null){
            hql+=" and t.dcode='"+dcode+"'" ;
        }
        //标题
        if(!"".equals(title)&&title!=null){
            hql+=" and t.title='"+title+"'" ;
        }
        //模板类型
        if(!"".equals(mblx)&&mblx!=null){
            hql+=" and t.mblx='"+mblx+"'" ;
        }


        if("".equals(pubFlag)||pubFlag==null){
            throw  new Exception("缺少pubFlag，公共私有数据标识。0表示私有数据，1表示公共数据");
        }

        if("1".equals(pubFlag)){
            hql += " and t.doctorId='0'" ;
        }

        if("0".equals(pubFlag)){
            if(doctorId==null||"".equals(doctorId)){
                throw  new Exception("缺少doctorId，用户标识");
            }
//            if(deptId==null||"".equals(deptId)){
//                throw  new Exception("缺少deptId，科室标识");
//            }
            hql+=" and t.doctorId='"+doctorId+"' " ;//or (t.deptId='"+deptId+"' and t.deptId <>'0') 只能看自己的，同一科室的也看不到
        }

        List<YunDisTemplet> yunDisTemplets = baseFacade.createQuery(YunDisTemplet.class, hql, new ArrayList<Object>()).getResultList();
        return yunDisTemplets;
    }


    /**
     * 添加或者修改表单模板
     * @param yunDisTemplet
     * @return
     */
    @POST
    @Path("merge")
    @Transactional
    public Response mergeYunDisTemplate(YunDisTemplet yunDisTemplet) throws Exception{
        if(yunDisTemplet.getId()!=null && !"".equals(yunDisTemplet.getId())){//修改表单模板 则将关联的病例数据进行更新
            String hql = " from YunDisTemplet where id = '"+yunDisTemplet.getId()+"'";
            List<YunDisTemplet> yunDisTempletList = baseFacade.createQuery(YunDisTemplet.class,hql,new ArrayList<Object>()).getResultList();
            if(yunDisTempletList!=null && !yunDisTempletList.isEmpty()){
                YunDisTemplet yunDisTempletQ = yunDisTempletList.get(0);
                if(yunDisTempletQ.getTitle()!=null && !yunDisTempletQ.getTitle().equals(yunDisTemplet.getTitle())){
                    String folderHql = "select d from YunRecordDocment as d,YunFolder as f,YunPatient as p where d.folderId = f.id" +
                            " and f.patientId = p.id and d.category = 'W' and f.diagnosisCode = '"+yunDisTemplet.getDcode()+"'" +
                            " and p.doctorId = '"+yunDisTemplet.getDoctorId()+"'";
                    List<YunRecordDocment> yunRecordDocmentList = baseFacade.createQuery(YunRecordDocment.class,folderHql,new ArrayList<Object>()).getResultList();
                    //changeRecordDocmentContent(yunRecordDocmentList,yunDisTempletQ,yunDisTemplet);
                }
            }
        }
        YunDisTemplet merge = baseFacade.merge(yunDisTemplet);
        return Response.status(Response.Status.OK).entity(merge).build();
    }

    public void changeRecordDocmentContent(List<YunRecordDocment> yunRecordDocmentList,YunDisTemplet dbtemplet,YunDisTemplet cmttemplet) throws Exception{
        if(yunRecordDocmentList!=null && !yunRecordDocmentList.isEmpty()){
            for(int i=0;i<yunRecordDocmentList.size();i++){
                YunRecordDocment yunRecordDocment = yunRecordDocmentList.get(i);
                DocumentData documentData = (DocumentData)JSONUtil.JSONToObj(yunRecordDocment.getContent(),DocumentData.class);
                List<DocumentDataElement> documentDataElements = documentData.getData();
                for(int k=0;k<documentDataElements.size();k++){
                    DocumentDataElement documentDataElement =  documentDataElements.get(k);
                    if(documentDataElement.getName()!=null && documentDataElement.getName().equals(dbtemplet.getTitle())){
                        documentDataElement.setName(cmttemplet.getTitle());
                    }
                }
                documentData.setData(documentDataElements);
                yunRecordDocment.setContent(JSONUtil.objectToJsonString(documentData));
                baseFacade.merge(yunRecordDocment);
            }
        }
    }
    /**
     * 删除模板
     * @param templateId
     * @return
     */
    @POST
    @Path("del")
    @Transactional
    public Response removeDisTemplate(@QueryParam("templateId") String templateId){
        YunDisTemplet templet = baseFacade.get(YunDisTemplet.class, templateId);
        baseFacade.remove(templet);
        return Response.status(Response.Status.OK).entity(templet).build();
    }


    /**
     * 获取模板设计的内容
     * @param templateId
     * @return
     * @throws Exception
     */
    @GET
    @Path("get-form-design")
    public Form getFormInfo(@QueryParam("templateId") String templateId) throws Exception {
        YunDisTemplet yunDisTemplet = baseFacade.get(YunDisTemplet.class,templateId);
        if(yunDisTemplet==null){
            throw  new Exception("找不到对应的模板信息");
        }
        String mbsj = yunDisTemplet.getMbsj() ;
        if(mbsj==null||"".equals(mbsj)){
            return null ;
        }
        Form form =getFormData(mbsj);
        return form ;
    }

    /**
     * 根据模板数据设置Form
     * @param mbsj
     * @return
     * @throws Exception
     */
    private Form getFormData(String mbsj) throws Exception {

        mbsj = replaceMbsj(mbsj);
        Form o = (Form) JSONUtil.JSONToObj(mbsj, Form.class);

        List<FormData> form_datas = o.getForm_data();
        FormPage formPage = new FormPage();

        for(FormData formData :form_datas){
            ModelPage modelPage = new ModelPage();
            modelPage.setTitle(formData.getName());
            modelPage.setValue(formData.getValue());

            List<Row> rows = formData.getRows();
            List<RowObject> rowssubjects = modelPage.getRowssubjects();
            Map<String,RowObject> rowObjectMap = new HashMap<String,RowObject>();
            for (Row row:rows){
                RowObject rowObject = new RowObject();
                //setRowObject(row,rowObject);//赋值
                //rowssubjects.add(rowObject);
                rowObjectMap.put(JSONUtil.objectToJsonString(row),rowObject);
            }
            System.out.println(rowObjectMap.size()+"条");
            long stime = System.currentTimeMillis();
            try{
                rowObjectMap = getRowObjectMapValue(rowObjectMap);
                for (Row row:rows){
                    String key = JSONUtil.objectToJsonString(row);
                    rowssubjects.add(rowObjectMap.get(key));
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            long etime = System.currentTimeMillis();
            System.out.println("花费时间"+(etime-stime)+"毫秒");
            formPage.getPages().add(modelPage);
        }
        o.setForm_template(formPage);
        return  o;
    }

    public Map<String,RowObject> getRowObjectMapValue(Map<String,RowObject> rowObjectMap) throws Exception{
        YunUsers yunUsers = UserUtils.getYunUsers();
        String deptId = yunUsers.getDeptId();
        String id = yunUsers.getId();
        Map<String,ElementRow> colElementRowMap = new HashMap<>();
        StringBuffer colValuesBuf = new StringBuffer("");
        if(rowObjectMap!=null && !rowObjectMap.isEmpty()){
            for(String key:rowObjectMap.keySet()){
                Row row = (Row)JSONUtil.JSONToObj(key, Row.class);
                RowObject rowObject = rowObjectMap.get(key);
                if(row.getCols()!=null && !row.getCols().isEmpty()){
                    rowObject.setColumn(String.valueOf(12/row.getCols().size()));
                    for (Col col:row.getCols()){
                        ElementRow elementRow = new ElementRow();
                        elementRow.setName(col.getValue());
                        colElementRowMap.put(StringUtils.replaceBank(col.getValue()),elementRow);
                        colValuesBuf.append("'").append(StringUtils.replaceBank(col.getValue())).append("',");
                    }
                }
            }
        }
        String colValues = colValuesBuf.toString();
        if(!StringUtils.isEmpty(colValues)){
            Map<String,String> colFmtMap = new HashMap<>();
            colValues = colValues.substring(0,colValues.length()-1);
            String valueFmtSql = "select v.name,t.format,v.doctor_id,t.dict,t.relyon from yun_value_format as t,yun_value as v where t.id = v.id" +
                    " and v.name in ("+colValues+")";
            List valueFmtList = baseFacade.createNativeQuery(valueFmtSql).getResultList();
            if(valueFmtList!=null && !valueFmtList.isEmpty()){
                int size = valueFmtList.size();
                for(int i=0;i<size;i++){
                    Object[] params = (Object[])valueFmtList.get(i);
                    String name = params[0]+"";
                    name = StringUtils.replaceBank(name);
                    if(!colFmtMap.containsKey(name)){
                        String doctorId = (params[2]==null?"":params[2].toString());
                        String dict = (params[3]==null?"":params[3].toString());
                        String relyon = (params[4]==null?"":params[4].toString());
                        colFmtMap.put(name,params[1]+"@"+doctorId+"@"+dict+"@"+relyon);
                    }
                }
            }
            for(String key:colElementRowMap.keySet()){
                if(!colFmtMap.containsKey(key)){
                    String valueFmtLikeSql = "select v.name,t.format,v.doctor_id,t.dict,t.relyon from yun_value_format as t,yun_value as v where t.id = v.id" +
                            " and v.name like '"+key+"%'";
                    List valueFmtLikeList = baseFacade.createNativeQuery(valueFmtLikeSql).getResultList();
                    if(valueFmtLikeList!=null && !valueFmtLikeList.isEmpty()){
                        Object[] params = (Object[])valueFmtLikeList.get(0);
                        String name = params[0]+"";
                        name = StringUtils.replaceBank(name);
                        if(!colFmtMap.containsKey(name)){
                            String doctorId = (params[2]==null?"":params[2].toString());
                            String dict = (params[3]==null?"":params[3].toString());
                            String relyon = (params[4]==null?"":params[4].toString());
                            //colFmtMap.put(name,params[1]+"@"+doctorId+"@"+dict+"@"+relyon);
                            colFmtMap.put(key,params[1]+"@"+doctorId+"@"+dict+"@"+relyon);
                        }
                    }else{
                        throw new Exception("获取名称为【"+key+"】的元数据格式失败！");
                    }
                }
            }
            StringBuffer dictsBuffer = new StringBuffer("");//存储dict字典信息内容
            Map<String,String> valueToDictMap = new HashMap<>();
            Map<String,List<YunDictitemVo>> valueYunDictitemMap = new HashMap<>();
            for(String key:colElementRowMap.keySet()){
                ElementRow elementRow = colElementRowMap.get(key);
                String params = colFmtMap.get(key);
                if(StringUtils.isEmpty(params)){
                    continue;
                }
                String[] paramArray = params.split("@");
                String format = paramArray[0];
                String doctorId = paramArray.length>1?paramArray[1]:"";
                String dict = paramArray.length>2?paramArray[2]:"";
                String relyon = paramArray.length>3?paramArray[3]:"";
                DataElementFormat dataElement = (DataElementFormat) JSONUtil.JSONToObj(format, DataElementFormat.class);
                Extend extend = new Extend();
                String extend1 = dataElement.getExtend();
                if(extend1!=null&&!"".equals(extend1)){
                    Extend obj = (Extend) JSONUtil.JSONToObj(extend1,Extend.class);
                    extend.setHead(obj.getHead());
                    extend.setPlac(obj.getPlac());
                    extend.setTail(obj.getTail());
                }else{
                    extend.setHead(dataElement.getHead());
                    extend.setPlac(dataElement.getPlac());
                    extend.setTail(dataElement.getTail());
                }
                String relyonvalue = dataElement.getRelyonvalue();
                extend.setRelyon(StringUtils.replaceBank(relyon));//relyon 可能含有- 需转换下
                extend.setRelyonvalue(relyonvalue);
                extend.setTemplet(dataElement.getTemplet());
                elementRow.setExtend(extend);
                elementRow.setType(dataElement.getPart());
                elementRow.setName(key);
                if(!StringUtils.isEmpty(dict)){//元数据下字典不为空
                    dictsBuffer.append("'").append(dict).append("',");
                    valueToDictMap.put(key,dict+"@"+doctorId);
                }
            }
            String dictsNames = dictsBuffer.toString();
            if(!StringUtils.isEmpty(dictsNames)){
                dictsNames = dictsNames.substring(0,dictsNames.length()-1);
                String hqlDict = "select new com.dchealth.VO.YunDictitemVo(yi.id,yd.typeName,yi.itemCode,yi.itemName,yi.inputCode,yi.loincCode) from " +
                        " YunDicttype as yd,YunDictitem yi  where yd.id=yi.typeIdDm and yd.typeName in("+dictsNames+")" +
                        " and (( yd.userId='"+id+"') or (yd.deptId='"+deptId+"' and yd.deptId<>'0'))" +
                        " order by yd.userId ,yd.deptId desc" ;
                List<YunDictitemVo> resultList1 = baseFacade.createQuery(YunDictitemVo.class, hqlDict, new ArrayList<Object>()).getResultList();
                if(resultList1!=null && !resultList1.isEmpty()){
                    for(YunDictitemVo yunDictitemVo:resultList1){
                        String typeName = yunDictitemVo.getTypeName();
                        if(valueYunDictitemMap.get(typeName)!=null){
                            List<YunDictitemVo> yunDictitemVos = valueYunDictitemMap.get(typeName);
                            yunDictitemVos.add(yunDictitemVo);
                        }else{
                            List<YunDictitemVo> yunDictitemVos = new ArrayList<>();
                            yunDictitemVos.add(yunDictitemVo);
                            valueYunDictitemMap.put(typeName,yunDictitemVos);
                        }
                    }
                }
                StringBuffer pubDictsBf = new StringBuffer("");
                for(String key:valueToDictMap.keySet()){
                    String value = valueToDictMap.get(key);
                    String dict = value.split("@")[0];
                    if(!valueYunDictitemMap.containsKey(dict)){
                        pubDictsBf.append("'").append(dict).append("',");
                    }
                }
                String pubDictNames = pubDictsBf.toString();
                if(!StringUtils.isEmpty(pubDictNames)){
                    pubDictNames = pubDictNames.substring(0,pubDictNames.length()-1);
                    String hqlPubDict = "select new com.dchealth.VO.YunDictitemVo(yi.id,yd.typeName,yi.itemCode,yi.itemName,yi.inputCode,yi.loincCode)" +
                            " from YunDicttype as yd,YunDictitem yi  where yd.id=yi.typeIdDm and yd.typeName in("+pubDictNames+") " +
                            " and yd.userId='0'" ;
                    List<YunDictitemVo> resultList2 = baseFacade.createQuery(YunDictitemVo.class, hqlPubDict, new ArrayList<Object>()).getResultList();
                    if(resultList2!=null && !resultList2.isEmpty()){
                        for(YunDictitemVo yunDictitemVo:resultList2){
                            String typeName = yunDictitemVo.getTypeName();
                            if(valueYunDictitemMap.get(typeName)!=null){
                                List<YunDictitemVo> yunDictitemVos = valueYunDictitemMap.get(typeName);
                                yunDictitemVos.add(yunDictitemVo);
                            }else{
                                List<YunDictitemVo> yunDictitemVos = new ArrayList<>();
                                yunDictitemVos.add(yunDictitemVo);
                                valueYunDictitemMap.put(typeName,yunDictitemVos);
                            }
                        }
                    }
                }
                for(String key:valueToDictMap.keySet()){
                    String value = valueToDictMap.get(key);
                    String dict = value.split("@")[0];
                    String doctorId = value.split("@")[1];
                    if(!valueYunDictitemMap.containsKey(dict)){
                        List<YunDictitemVo> yunDictitemVos = getOtherDoctorDictitem(dict,doctorId);
                        valueYunDictitemMap.put(dict,yunDictitemVos);
                    }
                }
                for(String key:colElementRowMap.keySet()){
                    ElementRow elementRow = colElementRowMap.get(key);
                    String dictAndDoctor = valueToDictMap.get(key);
                    if(!StringUtils.isEmpty(dictAndDoctor)){
                        String dict = dictAndDoctor.split("@")[0];
                        List<YunDictitemVo> yunDictitemVos = valueYunDictitemMap.get(dict);
                        for(YunDictitemVo yunDictitemVo:yunDictitemVos){
                            RowItem rowItem = new RowItem();
                            rowItem.setInputCode(yunDictitemVo.getInputCode());
                            rowItem.setName(StringUtils.replaceBank(key));//value 是否修改
                            rowItem.setText(yunDictitemVo.getItemName());
                            rowItem.setValue(yunDictitemVo.getItemCode());
                            rowItem.setLoincCode(yunDictitemVo.getLoincCode());
                            elementRow.getItems().add(rowItem);
                        }
                    }
                }
            }
            for(String key:rowObjectMap.keySet()){
                Row row = (Row)JSONUtil.JSONToObj(key, Row.class);
                RowObject rowObject = rowObjectMap.get(key);
                if(row.getCols()!=null && !row.getCols().isEmpty()){
                    for(Col col:row.getCols()){
                        String colValue = StringUtils.replaceBank(col.getValue());
                        rowObject.getRows().add(colElementRowMap.get(colValue));
                    }
                }
            }
        }
        return rowObjectMap;
    }

    /**
     * 根据字典名称和医生id获取相应的字典信息
     * @param dict
     * @param valueDoctorId
     * @return
     * @throws Exception
     */
    public List<YunDictitemVo> getOtherDoctorDictitem(String dict,String valueDoctorId) throws Exception{
        String hqlOther = "select new com.dchealth.VO.YunDictitemVo(yi.id,yd.typeName,yi.itemCode,yi.itemName,yi.inputCode,yi.loincCode)" +
                " from YunDicttype as yd,YunDictitem yi  where yd.id=yi.typeIdDm and yd.typeName='"+dict+"'" +
                " and yd.userId='"+valueDoctorId+"' ";
        List<YunDictitemVo> resultList = baseFacade.createQuery(YunDictitemVo.class, hqlOther, new ArrayList<Object>()).getResultList();
        if(resultList.size()<1){
            throw new Exception("获取名称为【"+dict+"】的字典失败");
        }
        return resultList;
    }
    /**
     * 由于模板数据存有错误的英文字段，因此要转换下
     * @param mbsj
     * @return
     */
    public String replaceMbsj(String mbsj){
        if(!StringUtils.isEmpty(mbsj) && mbsj.contains("inputcode")){
            mbsj = mbsj.replace("inputcode","inputCode");
        }
        return mbsj;
    }
    /**
     * 获取私有的模板设计内容
     * @param dcode
     * @param title
     * @return
     * @throws Exception
     */
    @GET
    @Path("get-private-work-form")
    public Form getPrivateTemplateForm(@QueryParam("dcode")String dcode,@QueryParam("title")String title) throws Exception {
        YunUsers yunUsers = UserUtils.getYunUsers();
        String hqlPrivate = "select t.mbsj from YunDisTemplet as t where t.dcode='"+dcode+"' and t.title='"+title+"' and (t.doctorId='"+yunUsers.getId()+"'" +
                " or (t.deptId='"+yunUsers.getDeptId()+"' and t.deptId<>'0'))" ;
        List<String> yunDisTemplets = baseFacade.createQuery(String.class, hqlPrivate, new ArrayList<Object>()).getResultList();
        if(yunDisTemplets!=null && !yunDisTemplets.isEmpty()){
            //YunDisTemplet tmplate = yunDisTemplets.get(0);
            String mbsj = yunDisTemplets.get(0);
            if(mbsj!=null&&!"".equals(mbsj)){
                return getFormData(mbsj);
            }else{
                return  null ;
            }
        }else{
            return null ;
        }
    }

    /**
     * 获取私有的模板设计内容
     * @param dcode
     * @param title
     * @return
     * @throws Exception
     */
    @GET
    @Path("get-private-work-form-V2")
    public Form getPrivateTemplateFormV2(@QueryParam("dcode")String dcode,@QueryParam("title")String title) throws Exception {
        YunUsers yunUsers = UserUtils.getYunUsers();
        String hqlPrivate = "select t.mbsj from YunDisTemplet as t where t.dcode='"+dcode+"' and t.title='"+title+"' and (t.doctorId='"+yunUsers.getId()+"'" +
                " or (t.deptId='"+yunUsers.getDeptId()+"' and t.deptId<>'0'))" ;
        List<String> yunDisTemplets = baseFacade.createQuery(String.class, hqlPrivate, new ArrayList<Object>()).getResultList();
        if(yunDisTemplets!=null && !yunDisTemplets.isEmpty()){
            //YunDisTemplet tmplate = yunDisTemplets.get(0);
            String mbsj = yunDisTemplets.get(0);
            if(mbsj!=null&&!"".equals(mbsj)){
                return getFormData(mbsj);
            }else{
                return  null ;
            }
        }else{
            return null ;
        }
    }

    /**
     * 获取已经发布的表单
     * @param dcode
     * @param title
     * @return
     * @throws Exception
     */
    @GET
    @Path("get-pub-work-form")
    public Form getPubTemplateForm(@QueryParam("dcode")String dcode,@QueryParam("title")String title) throws Exception {
        String hql = "select mbsj from YunReleaseTemplet as r where r.hstatus='R' and  r.dcode='"+dcode+"' and r.title='"+title+"'" ;
        List<String> resultList = baseFacade.createQuery(String.class, hql, new ArrayList<Object>()).getResultList();
        if(resultList!=null && !resultList.isEmpty()){
            //YunReleaseTemplet yunReleaseTemplet = resultList.get(0);
            String mbsj = resultList.get(0);
            if(mbsj!=null&&!"".equals(mbsj)){
                return getFormData(mbsj);
            }else{
                return  null ;
            }
        }else{
            return null;
        }
    }


    /**
     * 根据疾病编码和标题获取表单数据
     * @param dcode
     * @param title
     * @return
     * @throws Exception
     */
    @GET
    @Path("get-work-form")
    public Form getReleaseInfo(@QueryParam("dcode")String dcode,@QueryParam("title")String title) throws Exception {

        Form pubTemplateForm = getPubTemplateForm(dcode, title);
        if(pubTemplateForm==null){
            Form privateTemplateForm = getPrivateTemplateForm(dcode, title);
            return privateTemplateForm;
        }else{
            return pubTemplateForm;
        }

    }

    /**
     * 根据formData Row 设置templateObject 的rowObject
     * @param row
     * @param rowObject
     */
    private void setRowObject(Row row, RowObject rowObject) throws Exception {

        List<Col> cols = row.getCols();
        if(cols.size()>0){
            rowObject.setColumn(String.valueOf(12/cols.size()));
        }

        List<ElementRow> rows = rowObject.getRows();
        for (Col col:cols){
            ElementRow elementRow = new ElementRow();
            elementRow.setName(col.getValue());
            setElementRow(elementRow,col);
            rows.add(elementRow);
        }

    }

    /**
     * 根据列设置行元素
     * @param elementRow
     * @param col
     */
    private void setElementRow(ElementRow elementRow, Col col) throws Exception {
        String value = col.getValue();
        YunUsers yunUsers = UserUtils.getYunUsers();
        String deptId = yunUsers.getDeptId();
        String id = yunUsers.getId();
//        String hql = "select t from YunValueFormat t,YunValue as v  where t.id=v.id and  v.name='"+value+"" +
//                "' and ((v.doctorId='0' and v.deptId='0') or (v.doctorId='"+id+"' and v.deptId='"+deptId+"')" +
//                " or (v.doctorId='"+id+"' and v.deptId='0'))" ;
        value = StringUtils.replaceBank(value);//元数据格式横杆线改成下划线即 '-'改为'_'
        String hql = "select t from YunValueFormat t,YunValue as v  where t.id=v.id and  v.name='"+value+"'";

        List<YunValueFormat> resultList = baseFacade.createQuery(YunValueFormat.class, hql, new ArrayList<Object>()).getResultList();
        if(resultList.size()<1){
            String hql2 = "select t from YunValueFormat t,YunValue as v  where t.id=v.id and  v.name like '"+value+"%'";
            resultList = baseFacade.createQuery(YunValueFormat.class, hql2, new ArrayList<Object>()).getResultList();
            if(resultList.isEmpty()){
                throw new Exception("获取名称为【"+value+"】的元数据格式失败！");
            }
        }
        YunValueFormat yunValueFormat = resultList.get(0);
        YunValue yunValue =baseFacade.get(YunValue.class,yunValueFormat.getId());
        String valueDoctorId = yunValue.getDoctorId();

        DataElementFormat dataElement = (DataElementFormat) JSONUtil.JSONToObj(yunValueFormat.getFormat(), DataElementFormat.class);
        Extend extend = new Extend();
        String extend1 = dataElement.getExtend();
        if(extend1!=null&&!"".equals(extend1)){
            Extend obj = (Extend) JSONUtil.JSONToObj(extend1,Extend.class);
            extend.setHead(obj.getHead());
            extend.setPlac(obj.getPlac());
            extend.setTail(obj.getTail());
        }else{
            extend.setHead(dataElement.getHead());
            extend.setPlac(dataElement.getPlac());
            extend.setTail(dataElement.getTail());
        }
        String relyonvalue = dataElement.getRelyonvalue();

        String relyon = yunValueFormat.getRelyon();
        if(!"".equals(relyon)&&null!=relyon&&!"".equals(relyonvalue)&&null!=relyonvalue){

        }
        extend.setRelyon(StringUtils.replaceBank(relyon));//relyon 可能含有- 需转换下
        extend.setRelyonvalue(relyonvalue);

        extend.setTemplet(dataElement.getTemplet());
        elementRow.setExtend(extend);
        elementRow.setType(dataElement.getPart());
        elementRow.setName(StringUtils.replaceBank(value));//原先为带有'-'改为'_'
        String dict = yunValueFormat.getDict();

        if(dict!=null&&!"".equals(dict)){
            String hqlDict = "select yi from YunDicttype as yd,YunDictitem yi  where yd.id=yi.typeIdDm and yd.typeName='"+dict+"'" +
                    " and (( yd.userId='"+id+"') or (yd.deptId='"+deptId+"' and yd.deptId<>'0'))" +
                    " order by yd.userId ,yd.deptId desc" ;
            List<YunDictitem> resultList1 = baseFacade.createQuery(YunDictitem.class, hqlDict, new ArrayList<Object>()).getResultList();
            if(resultList1.size()<1){
                String hqlPubDict = "select yi from YunDicttype as yd,YunDictitem yi  where yd.id=yi.typeIdDm and yd.typeName='"+dict+"' " +
                        " and yd.userId='0'" ;
                resultList1 = baseFacade.createQuery(YunDictitem.class, hqlPubDict, new ArrayList<Object>()).getResultList();
                if(resultList1.size()<1){

                    String hqlOther = "select yi from YunDicttype as yd,YunDictitem yi  where yd.id=yi.typeIdDm and yd.typeName='"+dict+"'" +
                            " and yd.userId='"+valueDoctorId+"' ";
                    resultList1 = baseFacade.createQuery(YunDictitem.class, hqlOther, new ArrayList<Object>()).getResultList();
                    if(resultList1.size()<1){
                        throw new Exception("获取名称为【"+dict+"】的字典失败");
                    }
                }
            }

            for(YunDictitem yunDictitem:resultList1){
                RowItem rowItem = new RowItem();
                rowItem.setInputCode(yunDictitem.getInputCode());
                rowItem.setName(StringUtils.replaceBank(value));//value 是否修改
                rowItem.setText(yunDictitem.getItemName());
                rowItem.setValue(yunDictitem.getItemCode());
                rowItem.setLoincCode(yunDictitem.getLoincCode());
                elementRow.getItems().add(rowItem);
            }
        }
    }


    /**
     * 模板设计保存
     * @param form
     * @param
     * @return
     * @throws Exception
     */
    @POST
    @Path("merge-work-form-design")
    @Transactional
    public Response desginWorkFormInfo(Form form,@QueryParam("dcode")String dcode,@QueryParam("title")String title) throws Exception {
        YunUsers yunUsers = UserUtils.getYunUsers();
        String hqlPrivate = "from YunDisTemplet as t where t.dcode='"+dcode+"' and t.title='"+title+"' and (t.doctorId='"+yunUsers.getId()+"'" +
                " or (t.deptId='"+yunUsers.getDeptId()+"' and t.deptId<>'0'))" ;
        List<YunDisTemplet> yunDisTemplets = baseFacade.createQuery(YunDisTemplet.class, hqlPrivate, new ArrayList<Object>()).getResultList();
        if(yunDisTemplets.size()>0){
            YunDisTemplet templet = yunDisTemplets.get(0);
            String templateId = templet.getId();
            return this.desginFormInfo(form,templateId);
        }else {
            throw  new Exception("没有找到对应的模板！");
        }

    }

    /**
     * 模板设计保存
     * @param form
     * @param templateId
     * @return
     * @throws Exception
     */
    @POST
    @Path("merge-form-design")
    @Transactional
    public Response desginFormInfo(Form form,@QueryParam("templateId") String templateId) throws Exception {
        String mbsj = JSONUtil.objectToJson(form).toString();
        YunDisTemplet yunDisTemplet = baseFacade.get(YunDisTemplet.class, templateId);
        if(yunDisTemplet==null){
            throw new Exception("获取模板数据失败");
        }
        yunDisTemplet.setMbsj(mbsj);
        YunDisTemplet merge = baseFacade.merge(yunDisTemplet);
        return Response.status(Response.Status.OK).entity(merge).build();
    }
    /**
     * 发布表单或者工作流
     * @param templateId
     * @return
     */
    @Transactional
    @POST
    @Path("pub")
    public Response publishTemplateOrWork(@QueryParam("templateId") String templateId) throws Exception {

        YunDisTemplet yunDisTemplet = baseFacade.get(YunDisTemplet.class, templateId);
        if(yunDisTemplet==null){
            throw new Exception("") ;
        }

        String dcode = yunDisTemplet.getDcode() ;
        String title = yunDisTemplet.getTitle() ;
        String doctorId = yunDisTemplet.getDoctorId() ;
        String deptId = yunDisTemplet.getDeptId();
        String hql = "from YunReleaseTemplet as r where r.dcode='"+dcode+"'" +
                " and r.title='"+title+"'" ;
        Hversion hversion= new Hversion();
        hversion.setDept(deptId);
        hversion.setDoctor(doctorId);
        hversion.setNum("1");

        List<YunReleaseTemplet> resultList = baseFacade.createQuery(YunReleaseTemplet.class, hql, new ArrayList<Object>()).getResultList();
        for(YunReleaseTemplet templet:resultList){
            String hversion1 = templet.getHversion();
            Hversion tempHversion = (Hversion) JSONUtil.JSONToObj(hversion1, Hversion.class);
            templet.setHstatus("A");
            baseFacade.merge(templet);
        }
        YunReleaseTemplet yunReleaseTemplet = new YunReleaseTemplet();
        yunReleaseTemplet.setDcode(dcode);
        yunReleaseTemplet.setHstatus("C");
        yunReleaseTemplet.setMblx(yunDisTemplet.getMblx());
        yunReleaseTemplet.setMbsj(yunDisTemplet.getMbsj());
        yunReleaseTemplet.setModifyDate(new Timestamp(new Date().getTime()));
        yunReleaseTemplet.setNote(yunDisTemplet.getNote());
        yunReleaseTemplet.setTitle(title);
        yunReleaseTemplet.setValuedata("");
        yunReleaseTemplet.setHversion(JSONUtil.objectToJson(hversion).toString());
        YunReleaseTemplet releaseTemplet = baseFacade.merge(yunReleaseTemplet);
        return Response.status(Response.Status.OK).entity(releaseTemplet).build();

    }

    /**
     * 发布确认
     * @param relaseId
     * @return
     * @throws Exception
     */
    @POST
    @Transactional
    @Path("pub-confirm")
    public Response publishConfirmTemplateOrWork(@QueryParam("relaseId") String relaseId) throws Exception {

        YunReleaseTemplet yunReleaseTemplet = baseFacade.get(YunReleaseTemplet.class, relaseId);
        if(yunReleaseTemplet==null){
            throw  new Exception("没有获取到对应的信息");
        }
        yunReleaseTemplet.setModifyDate(new Timestamp(new Date().getTime()));
        yunReleaseTemplet.setHstatus("R");
        YunReleaseTemplet releaseTemplet = baseFacade.merge(yunReleaseTemplet);
        return Response.status(Response.Status.OK).entity(releaseTemplet).build();

    }

    /**
     * 备案文件添加病种和启动队列
     * @param yunReleaseTemplateVo
     * @return
     * @throws Exception
     */
    @POST
    @Transactional
    @Path("add-new-release")
    public Response mergeYunDiseaseTemplate(YunReleaseTemplateVo yunReleaseTemplateVo) throws Exception{
        YunReleaseTemplet yunReleaseTemplet = new YunReleaseTemplet();
        yunReleaseTemplet.setDcode(yunReleaseTemplateVo.getDcode());
        yunReleaseTemplet.setHstatus(yunReleaseTemplateVo.getHstatus());
        yunReleaseTemplet.setTitle(yunReleaseTemplateVo.getTitle());
        yunReleaseTemplet.setMblx(yunReleaseTemplateVo.getMblx());
        yunReleaseTemplet.setMbsj(JSONUtil.objectToJsonString(yunReleaseTemplateVo.getMbsj()));
        yunReleaseTemplet.setHversion(yunReleaseTemplateVo.getHversion());
        yunReleaseTemplet.setModifyDate(new Timestamp(new Date().getTime()));
        YunReleaseTemplet releaseTemplet = baseFacade.merge(yunReleaseTemplet);
        return  Response.status(Response.Status.OK).entity(releaseTemplet).build();
    }

    /**
     * 根据模板类型获取模板数据
     * @param mblx 模板类型
     * @return
     * @throws Exception
     */
    @GET
    @Path("list-yunrelease-template")
    public List<YunReleaseTemplet> getYunReleaseTemplate(@QueryParam("mblx")String mblx,@QueryParam("hstatus")String hstatus) throws Exception{
        String hql = "from YunReleaseTemplet as t where 1=1 " ;
        //所属疾病
        if(!"".equals(mblx) && mblx!=null){
            hql+=" and t.mblx='"+mblx+"'" ;
        }
        if(!"".equals(hstatus) && hstatus!=null){
                hql +=" and t.hstatus = '"+hstatus+"'";
        }
        List<YunReleaseTemplet> yunReleaseTempletList = baseFacade.createQuery(YunReleaseTemplet.class, hql, new ArrayList<Object>()).getResultList();
        Map<String,String> userMap = getUserMap(yunReleaseTempletList);
        return baseFacade.createQuery(YunReleaseTemplet.class, hql, new ArrayList<Object>()).getResultList();
    }

    public Map<String,String> getUserMap(List<YunReleaseTemplet> yunReleaseTempletList ) throws Exception{
        if(yunReleaseTempletList==null || yunReleaseTempletList.isEmpty()){
            return null;
        }
        String userIds = "";
        StringBuffer sb = new StringBuffer();
        int i=0;
        for(YunReleaseTemplet yunReleaseTemplet:yunReleaseTempletList){
            i++;
            String templateInfo = yunReleaseTemplet.getMbsj();
            JSONObject jsonObject = JSONUtil.objectToJson(templateInfo);
            jsonObject.get("userId");
            if(i%500==0){
                userIds="";
            }
        }
        return null;
    }
    /**
     * 根据资源ID获取模板资源数据
     * @param id
     * @return
     * @throws Exception
     */
    @GET
    @Path("yun-release-data")
    public YunReleaseTemplet getYunReleaseTempletById(@QueryParam("id")String id) throws Exception{
        String hql = "from YunReleaseTemplet as t where 1=1 ";
        if(id!=null && !"".equals(id)){
            hql += " and t.id = '"+id+"'";
        }
        YunReleaseTemplet yunReleaseTemplet = baseFacade.createQuery(YunReleaseTemplet.class, hql, new ArrayList<Object>()).getSingleResult();
        return yunReleaseTemplet;
    }

    /**
     * 根据传入的资源Id和状态进行审核
     * @param yunReleaseTemplateVo
     * @return
     */
    @POST
    @Path("update")
    @Transactional
    public Response updateYunReleaseTemplet(YunReleaseTemplateVo yunReleaseTemplateVo){
        YunReleaseTemplet yunReleaseTemplet = baseFacade.get(YunReleaseTemplet.class, yunReleaseTemplateVo.getId());
        String hstatus = yunReleaseTemplateVo.getHstatus();
        if(hstatus!=null && !"".equals(hstatus)){
            yunReleaseTemplet.setHstatus(hstatus);
        }
        YunReleaseTemplet releaseTemplet = baseFacade.merge(yunReleaseTemplet);
        return Response.status(Response.Status.OK).entity(releaseTemplet).build();
    }
    /**
     * 根据传入的模板id删除备案信息
     * @param id
     * @return
     */
    @POST
    @Path("delete-template")
    @Transactional
    public Response deleteYunReleaseTemplate(@QueryParam("id") String id){
        YunReleaseTemplet yunReleaseTemplet = baseFacade.get(YunReleaseTemplet.class, id);
        baseFacade.remove(yunReleaseTemplet);
        return Response.status(Response.Status.OK).entity(yunReleaseTemplet).build();
    }


    @GET
    @Path("get-value-html")
    public ElementRow getValueHtml(@QueryParam("name") String name ) throws Exception {
        String hql = "from YunValue as v where 1=1 " ;

        YunUsers yunUsers = UserUtils.getYunUsers();
        String doctorId = yunUsers.getId();
        String deptId = yunUsers.getDeptId();
        if(name!=null&&!"".equals(name)){
            hql+=" and v.name='"+name+"'" ;
        }

        String privateHql = hql + " and (v.doctorId='"+doctorId+"' or (v.deptId='"+deptId+"' and v.deptId <>'0'))" ;
        List<YunValue> yunValues = baseFacade.createQuery(YunValue.class,privateHql,new ArrayList<Object>()).getResultList();
        if(yunValues.size() !=1){
            String pubHql = hql + " and v.doctorId='0'";
            yunValues=baseFacade.createQuery(YunValue.class,pubHql,new ArrayList<Object>()).getResultList();
            if(yunValues.size()!=1){
                throw new Exception("没有找到名称为：【"+name+"】的元数据！");
            }
        }
        YunValue yunValue = yunValues.get(0);
        ElementRow elementRow = new ElementRow() ;
        Col col = new Col();
        col.setValue(yunValue.getName());
        this.setElementRow(elementRow,col);
        return elementRow;
    }
}
