package me.zhengjie.modules.customer.profile.domain;

import lombok.Data;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;

import java.io.Serializable;
import java.time.LocalDate;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;

/**
 * 一位客户在完成套餐映射与重复判定之后的导入候选。
 *
 * <p>预览与提交共用该结构：预览只读取 {@code draft}，提交在独立事务中首次建档/建单或续导唯一原订单；既有客户不重复创建地址，原订单不重复累计餐数。</p>
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class ImportCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作簿解析结果中的客户草稿
     */
    private ParsedCustomer parsed;

    /**
     * 面向操作人的客户草稿说明
     */
    private CustomerImportDraftDto draft;

    /**
     * 匹配到的父套餐；未匹配成功时为空
     */
    private ParentPackage parentPackage;

    /**
     * 该编号是否已存在于客户档案（幂等跳过）
     */
    private boolean alreadyExists;

    /** 编号与手机号一致时表示已有客户；完整导入可续导订单，仅第二页只补资料。 */
    private boolean supplemental;

    /** 预览时已存在的客户，用于冲突提示；确认时仍须按主键加锁复核。 */
    private CustomerProfile existingProfile;

    /**
     * 是否可以写入数据库
     */
    private boolean importable;

    /** 月份工作表的年月，以月首日保存；仅第二页模式为空。 */
    private LocalDate sourceMonth;

    /** 预览定位的唯一续导订单；没有订单时为空。 */
    private CustomerOrder existingOrder;

    /** 目标订单、数量和真实核销事实的并发修订摘要。 */
    private String orderRevision;
}
