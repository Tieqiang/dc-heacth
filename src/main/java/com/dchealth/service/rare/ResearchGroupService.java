package com.dchealth.service.rare;

import com.aliyun.mns.client.impl.queue.CreateQueueAction;
import com.dchealth.VO.InviteUserVo;
import com.dchealth.VO.Page;
import com.dchealth.VO.ResearchGroupVo;
import com.dchealth.entity.common.HospitalDict;
import com.dchealth.entity.common.YunUsers;
import com.dchealth.entity.rare.*;
import com.dchealth.facade.common.BaseFacade;
import com.dchealth.provider.MessagePush;
import com.dchealth.util.DwrScriptSessionManagerUtil;
import com.dchealth.util.StringUtils;
import com.dchealth.util.UserUtils;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.TypedQuery;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Created by Administrator on 2017/10/19.
 */
@Controller
@Produces("application/json")
@Path("research-group")
public class ResearchGroupService {

    @Autowired
    private BaseFacade baseFacade ;

    /**
     * 添加，修改，删除群组，删除为逻辑删除 status = -1
     * @param researchGroupVo
     * @return
     */
    @POST
    @Path("merge")
    @Transactional
    public Response mergeResearchGroup(ResearchGroupVo researchGroupVo) throws Exception{
        if("-1".equals(researchGroupVo.getStatus())){
            String delHospitalHql = " delete from ResearchGroupVsHospital where groupId = '"+researchGroupVo.getId()+"'";
            baseFacade.excHql(delHospitalHql);
            String delUser = "delete from ResearchGroupVsUser where groupId = '"+researchGroupVo.getId()+"'";
            baseFacade.excHql(delUser);
            ResearchGroup researchGroup = baseFacade.get(ResearchGroup.class,researchGroupVo.getId());
            researchGroup.setStatus(researchGroupVo.getStatus());
            ResearchGroup merge = baseFacade.merge(researchGroup);
            return Response.status(Response.Status.OK).entity(merge).build();
        }
        String sameHql = "select researchGroupName from ResearchGroup where status<>'-1' and researchGroupName = '"+researchGroupVo.getResearchGroupName()+"' and id<>'"+researchGroupVo.getId()+"'";
        List<String> stringList = baseFacade.createQuery(String.class,sameHql,new ArrayList<Object>()).getResultList();
        if(stringList!=null && !stringList.isEmpty()){
            throw new Exception("该群组名称已存在，请勿重新添加");
        }
        if(StringUtils.isEmpty(researchGroupVo.getId())){
            YunUsers yunUsers = UserUtils.getYunUsers();
            ResearchGroup researchGroup = new ResearchGroup();
            researchGroup.setDataShareLevel(researchGroupVo.getDataShareLevel());
            researchGroup.setGroupDesc(researchGroupVo.getGroupDesc());
            researchGroup.setGroupInInfo(researchGroupVo.getGroupInInfo());
            researchGroup.setManyHospitalFlag(researchGroupVo.getManyHospitalFlag());
            researchGroup.setResearchDiseaseId(researchGroupVo.getResearchDiseaseId());
            researchGroup.setResearchGroupName(researchGroupVo.getResearchGroupName());
            //researchGroup.setStatus(researchGroupVo.getStatus());
            researchGroup.setStatus("0");
            ResearchGroup merge = baseFacade.merge(researchGroup);
            List<String> hospitalDicts = null;
            if("0".equals(researchGroupVo.getManyHospitalFlag())){//单中心医院默认是自己的医院
                hospitalDicts = getHospitalDictByCode(yunUsers.getHospitalCode());
            }else{//多中心医院获取页面传的医院信息
                hospitalDicts = researchGroupVo.getHospitals();
                List<String> myHospitalDict = getHospitalDictByCode(yunUsers.getHospitalCode());
                if(hospitalDicts!=null){
                    if(myHospitalDict!=null){
                        hospitalDicts = getNonRepeatHospitals(hospitalDicts,myHospitalDict);
                    }
                }else{
                    throw new Exception("多中心医院选择不能为空");
                }
            }
            if(hospitalDicts!=null && !hospitalDicts.isEmpty()){
                List<ResearchGroupVsHospital> researchGroupVsHospitals = new ArrayList<>();
                for(String hospitalDict:hospitalDicts){
                    ResearchGroupVsHospital researchGroupVsHospital = new ResearchGroupVsHospital();
                    researchGroupVsHospital.setGroupId(merge.getId());
                    researchGroupVsHospital.setHospitalId(hospitalDict);
                    researchGroupVsHospitals.add(researchGroupVsHospital);
                    //ResearchGroupVsHospital mergeHospital = baseFacade.merge(researchGroupVsHospital);
                }
                baseFacade.batchInsert(researchGroupVsHospitals);
            }
            ResearchGroupVsUser researchGroupVsUser = new ResearchGroupVsUser();
            researchGroupVsUser.setLearderFlag("1");
            researchGroupVsUser.setCreaterFlag("1");
            researchGroupVsUser.setUserId(yunUsers.getId());
            researchGroupVsUser.setGroupId(merge.getId());
            baseFacade.merge(researchGroupVsUser);
            return Response.status(Response.Status.OK).entity(merge).build();
        }else{
            YunUsers yunUsers = UserUtils.getYunUsers();
            String delHospitalHql = " delete from ResearchGroupVsHospital where groupId = '"+researchGroupVo.getId()+"'";
            baseFacade.excHql(delHospitalHql);
            ResearchGroup researchGroup = baseFacade.get(ResearchGroup.class,researchGroupVo.getId());
            researchGroup.setDataShareLevel(researchGroupVo.getDataShareLevel());
            researchGroup.setGroupDesc(researchGroupVo.getGroupDesc());
            researchGroup.setGroupInInfo(researchGroupVo.getGroupInInfo());
            researchGroup.setManyHospitalFlag(researchGroupVo.getManyHospitalFlag());
            researchGroup.setResearchDiseaseId(researchGroupVo.getResearchDiseaseId());
            researchGroup.setResearchGroupName(researchGroupVo.getResearchGroupName());
            ResearchGroup merge = baseFacade.merge(researchGroup);

            List<String> hospitalDicts = null;
            List<String> myHospitalDict = getHospitalDictByCode(yunUsers.getHospitalCode());
            if("0".equals(researchGroupVo.getManyHospitalFlag())){//修改为单中心医院
                hospitalDicts = myHospitalDict;
            }else{
                if(researchGroupVo.getHospitals()!=null && !researchGroupVo.getHospitals().isEmpty()){
                    hospitalDicts = getNonRepeatHospitals(researchGroupVo.getHospitals(),myHospitalDict);
                }else{
                    hospitalDicts = myHospitalDict;
                }
            }
            if(hospitalDicts!=null && !hospitalDicts.isEmpty()){
                List<ResearchGroupVsHospital> researchGroupVsHospitals = new ArrayList<>();
                for(String hospitalDict:hospitalDicts){
                    ResearchGroupVsHospital researchGroupVsHospital = new ResearchGroupVsHospital();
                    researchGroupVsHospital.setGroupId(merge.getId());
                    researchGroupVsHospital.setHospitalId(hospitalDict);
                    //ResearchGroupVsHospital mergeHospital = baseFacade.merge(researchGroupVsHospital);
                    researchGroupVsHospitals.add(researchGroupVsHospital);
                }
                baseFacade.batchInsert(researchGroupVsHospitals);
            }
            return Response.status(Response.Status.OK).entity(merge).build();
        }
    }

    /**
     * 获取不重复的医院信息
     * @param hospitalDicts
     * @param myHospitalDict
     * @return
     */
    private List<String> getNonRepeatHospitals(List<String> hospitalDicts, List<String> myHospitalDict) {
        String hospitalDict = null;
        Boolean isHave = false;
        if(myHospitalDict!=null && !myHospitalDict.isEmpty()){
            hospitalDict = myHospitalDict.get(0);
            for(String hospitalDict1:hospitalDicts){
                if(hospitalDict1.equals(hospitalDict)){
                    isHave = true;
                }
            }
        }
        if(!isHave && hospitalDict!=null){
            hospitalDicts.add(hospitalDict);
        }
        return hospitalDicts;
    }

    /**
     * 根据医院编码查询医院信息
     * @param hospitalCode
     * @return
     */
    private List<String> getHospitalDictByCode(String hospitalCode) {
        String hql = "select id from HospitalDict where hospitalCode = '"+hospitalCode+"'";
        return baseFacade.createQuery(String.class,hql,new ArrayList<Object>()).getResultList();
    }

    /**
     *根据传入条件查询研究群组信息
     * @param groupName 研究组名称
     * @param diseaseId 多中心标志
     * @param userId 级别范围A，B两个级别
     * @param status 0表示正常申请，1表示审批通过 -1表示删除
     * @param perPage 每页条数
     * @param currentPage 当前页数
     * @return
     */
    @GET
    @Path("get-research-groups")
    public Page<ResearchGroupVo> getResearchGroups(@QueryParam("groupName")String groupName,@QueryParam("diseaseId")String diseaseId,
                                                 @QueryParam("userId")String userId,@QueryParam("status")String status,@QueryParam("userType")String userType,
                                                 @QueryParam("perPage") int perPage,@QueryParam("currentPage") int currentPage){
        String hql = "select new com.dchealth.VO.ResearchGroupVo(r.id,r.researchGroupName,r.researchDiseaseId,r.groupDesc,r.groupInInfo," +
                "r.manyHospitalFlag,r.dataShareLevel,r.status)  from ResearchGroup as r ";
        if("2".equals(userType)){
            hql += " ,YunUserDisease as u,YunDiseaseList as d where r.status <>'-1' and u.dcode = d.dcode and d.id = r.researchDiseaseId" +
                    " and  u.userId = '"+userId+"'";
        }else{
            hql += " where r.status <>'-1'";
        }
        if(!StringUtils.isEmpty(userId) && !StringUtils.isEmpty(userType)){
            if("0".equals(userType)){//userType=0表示查询出该用户创建的科研项目
                hql += " and exists(select 1 from ResearchGroupVsUser where groupId = r.id and userId = '"+userId+"' and createrFlag = '1')";
            }else if("1".equals(userType)){//该用户参与的群组
                hql += " and exists(select 1 from ResearchGroupVsUser where groupId = r.id and userId = '"+userId+"' and createrFlag = '0')";
            }else if("2".equals(userType)){//查询出该用户未参加的，切与本医院有关的群组
                hql += " and not exists(select 1 from ResearchGroupVsUser where groupId = r.id  and userId = '"+userId+"') " +
                        "and not exists(select 1 from InviteApplyRecord where groupId = r.id and status in ('0','1') and flag ='0' and userId = '"+userId+"')" +
                        " and r.id in (select groupId from ResearchGroupVsHospital where hospitalId = (select h.id from HospitalDict as h,YunUsers as u where " +
                        " u.hospitalCode = h.hospitalCode and u.id = '"+userId+"'))";//to_do
            }else if("3".equals(userType)){//查询出该用户参加待审核的，切与本医院有关的群组
                hql += " and exists(select 1 from InviteApplyRecord where groupId = r.id and status = '0' and flag ='0' and userId = '"+userId+"') and " +
                        " r.id in (select groupId from ResearchGroupVsHospital where hospitalId = (select h.id from HospitalDict as h,YunUsers as u where " +
                        " u.hospitalCode = h.hospitalCode and u.id = '"+userId+"'))";
            }
        }
        if(!StringUtils.isEmpty(groupName)){
            hql += " and r.researchGroupName like '%"+groupName+"%'";
        }
        if(!StringUtils.isEmpty(diseaseId)){
            hql += " and r.researchDiseaseId = '"+diseaseId+"'";
        }
        if(!StringUtils.isEmpty(status)){
            hql += " and r.status = '"+status+"'";
        }
        Page<ResearchGroupVo> researchGroupPage = baseFacade.getPageResult(ResearchGroupVo.class,hql,perPage,currentPage);
        Map<String,List<String>> map = new HashMap<String,List<String>>();
        StringBuffer groupIds = new StringBuffer("");
        for(ResearchGroupVo researchGroupVo:researchGroupPage.getData()){
            groupIds.append("'").append(researchGroupVo.getId()).append("',");
            if(map.get(researchGroupVo.getId())==null){
                List<String> hospitalDicts = new ArrayList<>();
                map.put(researchGroupVo.getId(),hospitalDicts);
            }
        }
        String groupIdsTo = groupIds.toString();
        if(!StringUtils.isEmpty(groupIdsTo)){
            groupIdsTo = groupIdsTo.substring(0,groupIdsTo.length()-1);
            String hospitalSql = "select h.id,r.group_id from hospital_dict as h,research_group_vs_hospital as r where h.status<>'-1' and r.hospital_id = h.id and r.group_id in ("+groupIdsTo+")";
            List list = baseFacade.createNativeQuery(hospitalSql).getResultList();
            if(list!=null && !list.isEmpty()){
                int size = list.size();
                for(int i=0;i<size;i++){
                    Object[] params = (Object[])list.get(i);
                    String groupId = (String)params[1];
                    if(map.get(groupId)!=null){
                        List<String> hospitalDicts = map.get(groupId);
                        hospitalDicts.add((String)params[0]);
                    }
                }
            }
            for(ResearchGroupVo researchGroupVo:researchGroupPage.getData()){
                researchGroupVo.setHospitals(map.get(researchGroupVo.getId()));
            }
        }
        return researchGroupPage;
    }

    /**
     *根据群组id获取群组信息
     * @param groupId 群组id
     * @return
     */
    @GET
    @Path("get-research-group")
    public ResearchGroupVo getResearchGroupById(@QueryParam("groupId")String groupId) throws Exception{
        String hql = " from ResearchGroup where id = '"+groupId+"'";
        List<ResearchGroup> researchGroups = baseFacade.createQuery(ResearchGroup.class,hql,new ArrayList<Object>()).getResultList();
        String hospitalSql = "select h.id from HospitalDict as h,ResearchGroupVsHospital as r where h.status<>'-1' and r.hospitalId = h.id and r.groupId  ='"+groupId+"'";
        List<String> hospitalDicts = baseFacade.createQuery(String.class,hospitalSql,new ArrayList<Object>()).getResultList();
        if(researchGroups!=null && !researchGroups.isEmpty()){
            ResearchGroupVo researchGroupVo = new ResearchGroupVo(researchGroups.get(0).getId(),researchGroups.get(0).getResearchGroupName(),
                    researchGroups.get(0).getResearchDiseaseId(),researchGroups.get(0).getGroupDesc(),researchGroups.get(0).getGroupInInfo(),
                    researchGroups.get(0).getManyHospitalFlag(),researchGroups.get(0).getDataShareLevel(),researchGroups.get(0).getStatus());
            researchGroupVo.setHospitals(hospitalDicts);
            return researchGroupVo;
        }else{
            throw new Exception("该群组信息不存在");
        }
    }

    /**
     *移除一个群组成员
     * @param groupId 群组id
     * @param userId 用户id
     * @return
     * @throws Exception
     */
    @POST
    @Path("remove-user")
    @Transactional
    public Response removeResearchGroupUser(@QueryParam("groupId")String groupId,@QueryParam("userId")String userId) throws Exception{
        if(StringUtils.isEmpty(groupId)){
            throw new Exception("群组id不能为空");
        }
        if(StringUtils.isEmpty(userId)){
            throw new Exception("用户id不能为空");
        }
        String delHql = "delete from ResearchGroupVsUser where groupId = '"+groupId+"' and userId = '"+userId+"'";
        baseFacade.excHql(delHql);
        //异常群组成员时更新申请记录为已移除
        String upHql = "update InviteApplyRecord set status = '2' where groupId = '"+groupId+"' and userId = '"+userId+"'";
        baseFacade.excHql(upHql);
        List<String> resultList = new ArrayList<>();
        resultList.add(groupId);
        return Response.status(Response.Status.OK).entity(resultList).build();
    }

    /**
     * 邀请一个人人员入组
     * @param groupId
     * @param userId
     * @return
     */
    @POST
    @Path("invite-research-user")
    @Transactional
    public Response inviteResearchUser(@QueryParam("groupId")String groupId,@QueryParam("userId")String userId) throws Exception{
        if(StringUtils.isEmpty(groupId)){
            throw new Exception("群组id不能为空");
        }
        if(StringUtils.isEmpty(userId)){
            throw new Exception("用户id不能为空");
        }
        String hql = "select userId from InviteApplyRecord where groupId = '"+groupId+"' and userId = '"+userId+"' and flag = '1'" +
                " and status = '0'";
        List list = baseFacade.createQuery(String.class,hql,new ArrayList<Object>()).getResultList();
        if(list!=null && !list.isEmpty()){
            throw new Exception("该用户已邀请，请勿重复邀请");
        }
        InviteApplyRecord inviteApplyRecord = new InviteApplyRecord();
        inviteApplyRecord.setGroupId(groupId);
        inviteApplyRecord.setUserId(userId);
        inviteApplyRecord.setFlag("1");
        inviteApplyRecord.setStatus("0");
        inviteApplyRecord.setCreateDate(new Date());
        InviteApplyRecord merge = baseFacade.merge(inviteApplyRecord);
        return Response.status(Response.Status.OK).entity(merge).build();
    }

    /**
     * 邀请一个人人员入组
     * @param groupId
     * @param userIds 用户id
     * @return
     */
    @POST
    @Path("invite-research-users")
    @Transactional
    public Response inviteResearchUser(@QueryParam("groupId")String groupId,List<String> userIds) throws Exception{
        List<InviteApplyRecord> inviteApplyRecords = new ArrayList<>();
        if(StringUtils.isEmpty(groupId)){
            throw new Exception("群组id不能为空");
        }
        if(userIds==null || userIds.isEmpty()){
            throw new Exception("用户id不能为空");
        }
        for(String userId:userIds){
            InviteApplyRecord inviteApplyRecord = new InviteApplyRecord();
            inviteApplyRecord.setGroupId(groupId);
            inviteApplyRecord.setUserId(userId);
            inviteApplyRecord.setFlag("1");
            inviteApplyRecord.setStatus("0");
            inviteApplyRecord.setCreateDate(new Date());
            InviteApplyRecord merge = baseFacade.merge(inviteApplyRecord);
            inviteApplyRecords.add(merge);
        }
        return Response.status(Response.Status.OK).entity(inviteApplyRecords).build();
    }
    /**
     * 申请一个群组加入
     * @param groupId
     * @param userId
     * @return
     */
    @POST
    @Path("apply-research-group")
    @Transactional
    public Response applyResearchGroup(@QueryParam("groupId")String groupId,@QueryParam("userId")String userId) throws Exception{
        if(StringUtils.isEmpty(groupId)){
            throw new Exception("群组id不能为空");
        }
        if(StringUtils.isEmpty(userId)){
            throw new Exception("用户id不能为空");
        }
        InviteApplyRecord inviteApplyRecord = new InviteApplyRecord();
        inviteApplyRecord.setGroupId(groupId);
        inviteApplyRecord.setUserId(userId);
        inviteApplyRecord.setFlag("0");
        inviteApplyRecord.setStatus("0");
        inviteApplyRecord.setCreateDate(new Date());
        InviteApplyRecord merge = baseFacade.merge(inviteApplyRecord);
        return Response.status(Response.Status.OK).entity(merge).build();
    }

    /**
     *审批或者同意个人入组
     * @param recordId 申请或者邀请记录Id
     * @return
     */
    @POST
    @Path("agree-group-user")
    @Transactional
    public Response agreeGroupUser(@QueryParam("recordId") String recordId) throws Exception{
        String hql = "from InviteApplyRecord where id = '"+recordId+"'";
        List<InviteApplyRecord> researchGroups = baseFacade.createQuery(InviteApplyRecord.class,hql,new ArrayList<Object>()).getResultList();
        if(researchGroups!=null && !researchGroups.isEmpty()){
            InviteApplyRecord inviteApplyRecord = researchGroups.get(0);
            inviteApplyRecord.setStatus("1");
            ResearchGroupVsUser researchGroupVsUser = new ResearchGroupVsUser();
            researchGroupVsUser.setGroupId(inviteApplyRecord.getGroupId());
            researchGroupVsUser.setUserId(inviteApplyRecord.getUserId());
            researchGroupVsUser.setCreaterFlag("0");
            researchGroupVsUser.setLearderFlag("0");
            baseFacade.merge(inviteApplyRecord);
            ResearchGroupVsUser merge = baseFacade.merge(researchGroupVsUser);
            return Response.status(Response.Status.OK).entity(merge).build();
        }else{
            throw new Exception("申请记录信息不存在");
        }
    }

    /**
     * 登录用户查看自己创建的组已邀请人员信息
     * @param userID 用户id 对应id 非userId
     * @param status 0表示待处理、1表示邀请人同意 -1表示拒绝
     * @param groupId   群组id
     * @param perPage 每页条数
     * @param currentPage 当前页
     *                    flag    1表示邀请   0表示申请
     * @return
     */
    @GET
    @Path("get-already-invite-users")
    public Page<InviteUserVo> getInviteUsers(@QueryParam("userID")String userID,@QueryParam("status")String status,@QueryParam("groupId")String groupId,
                                         @QueryParam("perPage") int perPage,@QueryParam("currentPage") int currentPage){
        return getInviteOrApplyUserVos(userID,status,"1",groupId,perPage,currentPage);
    }

    /**
     *根据用户id获取用户创建组申请人员信息
     * @param userID 用户id 对应id 非userId
     * @param status 0表示待处理、1表示邀请人同意 -1表示拒绝
     * @param groupId  群组id
     * @param perPage 每页条数
     * @param currentPage 当前页
     *                    flag    1表示邀请   0表示申请
     * @return
     */
    @GET
    @Path("get-already-apply-users")
    public Page<InviteUserVo> getApplyUsers(@QueryParam("userID")String userID,@QueryParam("status")String status,@QueryParam("groupId") String groupId,
                                             @QueryParam("perPage") int perPage,@QueryParam("currentPage") int currentPage){
        return getInviteOrApplyUserVos(userID,status,"0",groupId,perPage,currentPage);
    }
    /**
     *根据flag值判断是邀请或者申请，如果是邀请 获取当前用户邀请人员信息，如果为申请获取该用户创建组的申请人员信息
     * @param userID 用户id 对应id 非userId
     * @param status 0表示待处理、1表示邀请人同意 -1表示拒绝
     * @param flag 1表示邀请   0表示申请
     * @param groupId 群组id
     * @param perPage 每页条数
     * @param currentPage 当前页
     * @return
     */
    public Page<InviteUserVo> getInviteOrApplyUserVos(String userID,String status,String flag,String groupId,int perPage,int currentPage){
        String hql = "select new com.dchealth.VO.InviteUserVo(u.id,u.userName,u.sex,u.nation,u.mobile,u.tel,u.email,u.birthDate," +
                "u.title,u.hospitalName,g.researchGroupName as groupName,(select d.name from YunDiseaseList as d where d.id = g.researchDiseaseId) as diseaseName,p.id as recordId,p.status)" +
                " from YunUsers as u,ResearchGroup as g,InviteApplyRecord as p,ResearchGroupVsUser as c" +
                " where g.id = p.groupId and g.id = c.groupId and u.id = p.userId and p.flag = '"+flag+"' and " +
                " c.userId = '"+userID+"' and g.id = '"+groupId+"' ";
        String hqlCount = "select count(*) from YunUsers as u,ResearchGroup as g,InviteApplyRecord as p,ResearchGroupVsUser as c" +
                " where g.id = p.groupId and g.id = c.groupId and u.id = p.userId and p.flag = '"+flag+"' and " +
                " c.userId = '"+userID+"' and g.id = '"+groupId+"' ";
        if(!StringUtils.isEmpty(status)){
            hql += " and p.status = '"+status+"'";
            hqlCount += " and p.status = '"+status+"'";
        }

        Page<InviteUserVo> resultPage = new Page<>();
        TypedQuery<InviteUserVo> typedQuery = baseFacade.createQuery(InviteUserVo.class,hql,new ArrayList<Object>());
        Long counts =  baseFacade.createQuery(Long.class,hqlCount,new ArrayList<Object>()).getSingleResult();
        resultPage.setCounts(counts);
        if(perPage<=0){
            perPage =1;
        }
        if(currentPage<=0){
            currentPage=1;
        }
        typedQuery.setFirstResult((currentPage-1)*perPage);
        typedQuery.setMaxResults(perPage);
        resultPage.setPerPage((long)perPage);
        List<InviteUserVo> resultList = typedQuery.getResultList();
        resultPage.setData(resultList);
        return resultPage;
    }
    /**
     *flag=1表示获取邀请自己的记录 包含同意的，未同意的，待处理的，flag=0表示自己申请的记录信息
     * @param userID 用户id 对应id 非userId
     * @param status 0表示待处理、1表示邀请人同意 -1表示拒绝
     * flag    1表示邀请   0表示申请
     * @return
     */
    @GET
    @Path("get-invite-or-apply-records")
    public Page<InviteUserVo> getMyInviteApplyRecords(@QueryParam("userID")String userID,@QueryParam("status")String status,@QueryParam("flag")String flag,
                                                      @QueryParam("perPage") int perPage,@QueryParam("currentPage") int currentPage){
        String hql = "select new com.dchealth.VO.InviteUserVo(u.id,u.userName,u.sex,u.nation,u.mobile,u.tel,u.email,u.birthDate," +
                "u.title,u.hospitalName,g.researchGroupName as groupName,(select d.name from YunDiseaseList as d where d.id = g.researchDiseaseId) as diseaseName,p.id as recordId,p.status)" +
                " from YunUsers as u,ResearchGroup as g,InviteApplyRecord as p,ResearchGroupVsUser as c" +
                " where g.id = p.groupId and g.id = c.groupId  " ;
        String hqlCount = "select count(*) from YunUsers as u,ResearchGroup as g,InviteApplyRecord as p,ResearchGroupVsUser as c" +
                " where g.id = p.groupId and g.id = c.groupId  ";
        if("1".equals(flag)){//邀请的信息
            hql += " and u.id = c.userId and p.flag = '"+flag+"' and p.userId = '"+userID+"' and c.createrFlag = '1' ";
            hqlCount += " and u.id = c.userId and p.flag = '"+flag+"' and p.userId = '"+userID+"' and c.createrFlag = '1' ";
        }else{
            hql += " and u.id = p.userId and p.flag = '"+flag+"' and c.userId != '"+userID+"' and p.userId = '"+userID+"' ";
            hqlCount += " and u.id = p.userId and p.flag = '"+flag+"' and c.userId != '"+userID+"' and p.userId = '"+userID+"' ";
        }
        if(!StringUtils.isEmpty(status)){
            hql += " and p.status = '"+status+"'";
            hqlCount += " and p.status = '"+status+"'";
        }
        Page<InviteUserVo> resultPage = new Page<>();
        TypedQuery<InviteUserVo> typedQuery = baseFacade.createQuery(InviteUserVo.class,hql,new ArrayList<Object>());
        Long counts =  baseFacade.createQuery(Long.class,hqlCount,new ArrayList<Object>()).getSingleResult();
        resultPage.setCounts(counts);
        if(perPage<=0){
            perPage =1;
        }
        if(currentPage<=0){
            currentPage=1;
        }
        typedQuery.setFirstResult((currentPage-1)*perPage);
        typedQuery.setMaxResults(perPage);
        resultPage.setPerPage((long)perPage);
        List<InviteUserVo> resultList = typedQuery.getResultList();
        resultPage.setData(resultList);
        return resultPage;
    }

    /**
     *对群组邀请进行操作，同意或拒绝，或者申请进行操作，同意或拒绝
     * @param recordId 邀请记录id
     * @param status 状态值 0表示待处理、1表示同意-1表示为拒绝
     * @return
     */
    @POST
    @Path("confirm-invite-record")
    @Transactional
    public Response confirmInviteRecord(@QueryParam("recordId")String recordId,@QueryParam("status")String status) throws Exception{
        InviteApplyRecord inviteApplyRecord = null;
        try {
            inviteApplyRecord = baseFacade.get(InviteApplyRecord.class,recordId);
            if("0".equals(inviteApplyRecord.getStatus())){
                inviteApplyRecord.setStatus(status);
                inviteApplyRecord = baseFacade.merge(inviteApplyRecord);
                if("1".equals(status)){
                    ResearchGroupVsUser researchGroupVsUser = new ResearchGroupVsUser();
                    researchGroupVsUser.setLearderFlag("0");
                    researchGroupVsUser.setCreaterFlag("0");
                    researchGroupVsUser.setUserId(inviteApplyRecord.getUserId());
                    researchGroupVsUser.setGroupId(inviteApplyRecord.getGroupId());
                    baseFacade.merge(researchGroupVsUser);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new Exception("操作异常，请联系管理员");
        }
        return Response.status(Response.Status.OK).entity(inviteApplyRecord).build();
    }

    /**
     *获取群组下的成员信息
     * @param groupId 群组id
     * @param perPage 每页条数
     * @param currentPage 当前页
     * @return
     */
    @GET
    @Path("get-research-group-users")
    public Page<YunUsers> getResearchGroupUsers(@QueryParam("groupId")String groupId,@QueryParam("perPage") int perPage,@QueryParam("currentPage") int currentPage){
        String hql = "select u from YunUsers as u,ResearchGroupVsUser as v where u.id = v.userId and v.groupId = '"+groupId+"'";
        return baseFacade.getPageResult(YunUsers.class,hql,perPage,currentPage);
    }

    /**
     * 根据群组id和研究疾病获取相关的医生信息
     * @param groupId
     * @param researchDiseaseId
     * @return
     */
    @GET
    @Path("get-group-invite-users")
    public Page<YunUsers> getGroupInviteUsers(@QueryParam("groupId")String groupId,@QueryParam("researchDiseaseId")String researchDiseaseId,
                                         @QueryParam("userName")String userName,@QueryParam("perPage") int perPage,@QueryParam("currentPage") int currentPage) throws Exception{
        if(StringUtils.isEmpty(researchDiseaseId)){
            throw new Exception("疾病信息不能为空");
        }
        if(StringUtils.isEmpty(groupId)){
            throw new Exception("群组信息不能为空");
        }
        YunDiseaseList yunDiseaseList = baseFacade.get(YunDiseaseList.class,researchDiseaseId);
        String hql = "select u from YunUsers as u,ResearchGroupVsHospital as vs,ResearchGroup as g,HospitalDict as h" +
                " where g.status<>'-1' and g.id = vs.groupId and u.hospitalCode = h.hospitalCode and vs.hospitalId = h.id " +
                " and exists(select 1 from YunUserDisease where dcode = '"+yunDiseaseList.getDcode()+"' and userId = u.id)" +
                " and u.id not in(select userId from ResearchGroupVsUser where createrFlag = '1' and groupId = '"+groupId+"')" +
                " and g.researchDiseaseId = '"+researchDiseaseId+"' and g.id = '"+groupId+"'";
        if(!StringUtils.isEmpty(userName)){
            hql += " and u.userName like '"+userName+"%'";
        }
        return baseFacade.getPageResult(YunUsers.class,hql,perPage,currentPage);
    }

    @GET
    @Path("test-send")
    public List<String> sendNotice(){
        List list = new ArrayList();
        String userId = "zhang";
        DwrScriptSessionManagerUtil dwrScriptSessionManagerUtil = new DwrScriptSessionManagerUtil();
        dwrScriptSessionManagerUtil.sendMessageAuto(userId,"test");
        list.add(userId);
        return list;
    }
}