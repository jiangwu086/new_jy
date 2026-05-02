package com.chocwell.jy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chocwell.jy.entity.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CheckinMapper extends BaseMapper<CheckinRecord> {

    /**
     * 统计用户在指定周期内的有效打卡次数
     */
    @Select("SELECT COUNT(*) FROM checkin_record WHERE user_id = #{userId} AND period = #{period} AND status = 1")
    int countByUserPeriod(@Param("userId") Long userId, @Param("period") String period);

    /** 今日打卡总量 */
    @Select("SELECT COUNT(*) FROM checkin_record WHERE DATE(checkin_time) = CURRENT_DATE AND status = 1")
    int countToday();

    /** 本月打卡总量 */
    @Select("SELECT COUNT(*) FROM checkin_record WHERE DATE_TRUNC('month', checkin_time) = DATE_TRUNC('month', CURRENT_DATE) AND status = 1")
    int countThisMonth();

    /** 累计有效打卡总量 */
    @Select("SELECT COUNT(*) FROM checkin_record WHERE status = 1")
    int countAllValid();

    /** 近7天每天打卡趋势（返回 [{dt, cnt}]） */
    @Select("SELECT TO_CHAR(DATE(checkin_time),'MM-DD') AS dt, COUNT(*) AS cnt " +
            "FROM checkin_record " +
            "WHERE checkin_time >= CURRENT_DATE - INTERVAL '6 days' AND status = 1 " +
            "GROUP BY DATE(checkin_time) ORDER BY DATE(checkin_time)")
    List<Map<String, Object>> last7DaysTrend();

    /** 近30天每天打卡趋势（返回 [{dt, cnt}]） */
    @Select("SELECT TO_CHAR(d,'MM-DD') AS dt, COALESCE(COUNT(cr.id),0) AS cnt " +
            "FROM GENERATE_SERIES(CURRENT_DATE - INTERVAL '29 days', CURRENT_DATE, INTERVAL '1 day') d " +
            "LEFT JOIN checkin_record cr ON DATE(cr.checkin_time) = d::date AND cr.status = 1 " +
            "GROUP BY d ORDER BY d")
    List<Map<String, Object>> last30DaysTrend();

    /** 区县覆盖排名（按有效打卡人数统计） */
    @Select("SELECT COALESCE(NULLIF(el.district_code,''),'未分区') AS district_code, " +
            "COUNT(DISTINCT cr.user_id) AS workers, COUNT(*) AS checkins " +
            "FROM checkin_record cr " +
            "LEFT JOIN edu_location el ON cr.location_id = el.id " +
            "WHERE cr.status = 1 " +
            "GROUP BY COALESCE(NULLIF(el.district_code,''),'未分区') " +
            "ORDER BY workers DESC LIMIT 12")
    List<Map<String, Object>> districtCoverageRanking();

    /** 最近 N 条打卡（关联用户名、点位名） */
    @Select("SELECT cr.id, cr.checkin_time, cr.points_earned, cr.period, " +
            "su.real_name, su.nick_name, su.phone, el.name AS location_name " +
            "FROM checkin_record cr " +
            "LEFT JOIN sys_user  su ON cr.user_id     = su.id " +
            "LEFT JOIN edu_location el ON cr.location_id = el.id " +
            "WHERE cr.status = 1 " +
            "ORDER BY cr.checkin_time DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> recentCheckins(@Param("limit") int limit);

    /** 管理后台分页打卡记录（含关联信息） */
    @Select("<script>" +
            "SELECT cr.id, cr.user_id, cr.location_id, cr.checkin_time, cr.points_earned, cr.period, cr.status, " +
            "su.real_name, su.nick_name, su.phone, el.name AS location_name " +
            "FROM checkin_record cr " +
            "LEFT JOIN sys_user  su ON cr.user_id     = su.id " +
            "LEFT JOIN edu_location el ON cr.location_id = el.id " +
            "WHERE 1=1 " +
            "<if test='userId   != null'> AND cr.user_id     = #{userId}     </if>" +
            "<if test='locationId != null'> AND cr.location_id = #{locationId} </if>" +
            "<if test='period   != null and period   != \"\"'> AND cr.period = #{period} </if>" +
            "<if test='status   != null'> AND cr.status       = #{status}     </if>" +
            "ORDER BY cr.checkin_time DESC " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Map<String, Object>> pageAdminList(@Param("userId")     Long userId,
                                            @Param("locationId") Long locationId,
                                            @Param("period")     String period,
                                            @Param("status")     Integer status,
                                            @Param("size")       int size,
                                            @Param("offset")     int offset);

    /** 管理后台打卡记录总数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM checkin_record cr WHERE 1=1 " +
            "<if test='userId   != null'> AND cr.user_id     = #{userId}     </if>" +
            "<if test='locationId != null'> AND cr.location_id = #{locationId} </if>" +
            "<if test='period   != null and period   != \"\"'> AND cr.period = #{period} </if>" +
            "<if test='status   != null'> AND cr.status       = #{status}     </if>" +
            "</script>")
    long countAdminList(@Param("userId")     Long userId,
                        @Param("locationId") Long locationId,
                        @Param("period")     String period,
                        @Param("status")     Integer status);

    /** 机构端：指定点位列表的今日打卡数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM checkin_record WHERE status=1 AND DATE(checkin_time)=CURRENT_DATE" +
            " AND location_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int countTodayByLocations(@Param("ids") List<Long> locationIds);

    /** 机构端：指定点位列表的本月打卡数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM checkin_record WHERE status=1" +
            " AND DATE_TRUNC('month',checkin_time)=DATE_TRUNC('month',CURRENT_DATE)" +
            " AND location_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int countMonthByLocations(@Param("ids") List<Long> locationIds);

    /** 机构端：指定点位最近打卡 */
    @Select("<script>" +
            "SELECT cr.id, cr.checkin_time, cr.points_earned, cr.period, " +
            "su.real_name, su.nick_name, su.phone, el.name AS location_name " +
            "FROM checkin_record cr " +
            "LEFT JOIN sys_user su ON cr.user_id=su.id " +
            "LEFT JOIN edu_location el ON cr.location_id=el.id " +
            "WHERE cr.status=1 AND cr.location_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY cr.checkin_time DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> recentCheckinsByLocations(@Param("ids") List<Long> locationIds,
                                                        @Param("limit") int limit);

    /** 用户打卡历史（含点位名称、审核状态，最近 N 条；包含被驳回记录） */
    @Select("SELECT cr.id, cr.user_id, cr.location_id, cr.checkin_time, cr.points_earned, cr.period, " +
            "cr.status, cr.review_status, cr.review_remark, " +
            "el.name AS location_name, el.address AS location_address " +
            "FROM checkin_record cr " +
            "LEFT JOIN edu_location el ON cr.location_id = el.id " +
            "WHERE cr.user_id = #{userId} " +
            "ORDER BY cr.checkin_time DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> userHistory(@Param("userId") Long userId, @Param("limit") int limit);
}
