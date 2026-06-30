<template>
  <div class="production-sheet-wrapper">
    <!-- ── 顶部操作栏 ── -->
    <div class="action-bar no-print">
      <div class="action-bar__left">
        <span class="action-bar__title">排餐生产单</span>
      </div>
      <div class="action-bar__right">
        <!-- 日期 -->
        <el-date-picker
          v-model="queryDate"
          type="date"
          placeholder="选择日期"
          value-format="yyyy-MM-dd"
          size="small"
          style="width: 160px; margin-right: 10px;"
          @change="handleQuery"
        />
        <!-- 餐次 -->
        <el-select
          v-model="queryMealType"
          placeholder="选择餐次"
          size="small"
          style="width: 130px; margin-right: 10px;"
          @change="handleQuery"
        >
          <el-option label="早餐" value="BREAKFAST" />
          <el-option label="午餐" value="LUNCH" />
          <el-option label="晚餐" value="DINNER" />
        </el-select>
        <!-- 查询 -->
        <el-button size="small" icon="el-icon-search" type="primary" @click="handleQuery">查询</el-button>
        <el-divider direction="vertical" />
        <!-- 生成排餐计划 -->
        <el-button
          size="small"
          type="success"
          icon="el-icon-s-promotion"
          @click="openGenerateDialog"
        >生成排餐计划</el-button>
        <!-- 删除排餐记录 -->
        <el-button
          size="small"
          type="danger"
          icon="el-icon-delete"
          :disabled="!planData"
          @click="handleDeleteCurrent"
        >删除排餐记录</el-button>
        <el-divider direction="vertical" />
        <!-- 查询客户详情 -->
        <el-button
          size="small"
          type="info"
          icon="el-icon-user"
          :disabled="!planData"
          @click="openAddressDialog"
        >客户详情</el-button>
        <el-button
          size="small"
          type="primary"
          plain
          icon="el-icon-notebook-2"
          @click="rulesDialogVisible = true"
        >展示规则</el-button>
        <!-- 打印 -->
        <el-button size="small" icon="el-icon-printer" @click="handlePrint">打印预览</el-button>
        <el-button
          v-if="planData"
          size="small"
          type="warning"
          icon="el-icon-s-operation"
          @click="openReplaceDialog"
        >换菜管理</el-button>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" v-loading="loading" class="loading-mask" />

    <!-- 无数据 -->
    <div v-if="!loading && !planData" class="empty-state no-print">
      <i class="el-icon-document" />
      <p>暂无数据，请选择日期和餐次查询</p>
    </div>

    <!-- ── 生产单主体 ── -->
    <div v-if="planData" class="sheet-root">
      <!-- 页头 -->
      <div class="sheet-header">
        <div class="sheet-header__cell">
          <span class="cell-label">日期</span>
          <span class="cell-value">{{ formatDate(planData.mealPlan.recordDate) }}</span>
        </div>
        <div class="sheet-header__cell">
          <span class="cell-label">餐次</span>
          <span class="cell-value">{{ mealTypeText }}</span>
        </div>
        <div class="sheet-header__cell sheet-header__cell--right">
          <span class="cell-label">总人数</span>
          <span class="cell-value cell-value--hero">{{ planData.totalCustomers }}</span>
        </div>
      </div>

      <!-- 主体：左右两栏 -->
      <div class="sheet-body">
        <!-- 左栏：编号区 -->
        <div class="sheet-left">
          <div class="section-title">
            <span>编号区</span>
            <span class="section-badge">ITEM CODES</span>
          </div>
          <div class="code-grid">
            <div
              v-for="customer in allCustomers"
              :key="customer.id"
              class="code-cell"
            >
              <div class="code-main">
                <el-tooltip
                  v-if="customer.specialRequirements"
                  effect="dark"
                  placement="top"
                  :content="customer.specialRequirements"
                >
                  <span
                    class="code-text code-text--tooltip"
                    :class="{ 'code-text--soup-missing': customer.isSoupMissing }"
                  >{{ customer.customerCode || customer.customerName }}</span>
                </el-tooltip>
                <span
                  v-else
                  class="code-text"
                  :class="{ 'code-text--soup-missing': customer.isSoupMissing }"
                >{{ customer.customerCode || customer.customerName }}</span>
                <div v-if="customer.firstMealOfOrder || customer.nearProductionDate" class="code-badges">
                  <span v-if="customer.firstMealOfOrder" class="code-first-badge">首</span>
                  <el-tooltip
                    v-if="customer.nearProductionDate"
                    effect="dark"
                    placement="top"
                    :content="getProductionDateBadgeTip(customer)"
                  >
                    <span class="code-production-badge">产</span>
                  </el-tooltip>
                </div>
              </div>
              <div v-if="customer.specialRequirementTags && customer.specialRequirementTags.length > 0" class="special-requirement-tags">
                <span
                  v-for="(tag, idx) in customer.specialRequirementTags"
                  :key="idx"
                  class="special-requirement-tag"
                >{{ tag }}</span>
              </div>
              <div v-if="showSupplementaryTags && customer.supplementaryTags && customer.supplementaryTags.length > 0" class="supplementary-tags">
                <span
                  v-for="(tag, idx) in customer.supplementaryTags"
                  :key="idx"
                  class="supplementary-tag"
                  :class="{ 'supplementary-tag--missing': tag.startsWith('无') }"
                >{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右栏：菜单汇总 + 换菜明细 -->
        <div class="sheet-right">
          <!-- 右上：今日菜单汇总 -->
          <div class="sheet-right__top">
            <table class="dish-table">
              <thead>
                <tr>
                  <th class="col-category">类目</th>
                  <th class="col-name">菜名</th>
                  <th class="col-count">人数</th>
                  <th class="col-codes">编号明细</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(dish, idx) in regularDishes" :key="`reg-${idx}`">
                  <td class="col-category">
                    <span class="dish-type-tag" :class="`dish-type-tag--${dish.dishType.toLowerCase()}`">
                      {{ dishTypeMap[dish.dishType] || dish.dishType }}
                    </span>
                  </td>
                  <td class="col-name">{{ dish.dishName }}</td>
                  <td class="col-count">{{ dish.count }}</td>
                  <td class="col-codes">
                    <span class="codes-screen">{{ dish.codeSnippet }}</span>
                    <span class="codes-print">{{ dish.printCodeSnippet }}</span>
                  </td>
                </tr>
                <tr v-if="regularDishes.length === 0">
                  <td colspan="4" class="empty-row">暂无常规排餐</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="sheet-divider" />

          <!-- 右下：换菜明细 -->
          <div class="sheet-right__bottom">
            <table class="dish-table">
              <thead>
                <tr>
                  <th class="col-category">换菜</th>
                  <th class="col-name">替换项目</th>
                  <th class="col-count">人数</th>
                  <th class="col-codes">目标编号</th>
                </tr>
              </thead>
              <tbody>
                <template v-if="manualReplaceDishes.length > 0">
                  <tr v-for="(dish, idx) in manualReplaceDishes" :key="`mrep-${idx}`">
                    <td class="col-category">
                      <span class="dish-type-tag" :class="`dish-type-tag--${dish.dishType.toLowerCase()}`">
                        {{ dishTypeMap[dish.dishType] || dish.dishType }}
                      </span>
                    </td>
                    <td class="col-name">{{ dish.dishName }}</td>
                    <td class="col-count">{{ dish.count }}</td>
                    <td class="col-codes">{{ dish.codeSnippet }}</td>
                  </tr>
                </template>
                <template v-if="autoReplaceRows.length > 0">
                  <tr v-for="(dish, idx) in autoReplaceRows" :key="`rep-${idx}`" :class="{ 'replace-compact-row': dish.compactGroup }">
                    <td class="col-category">
                      <span v-if="dish.dishType" class="dish-type-tag" :class="`dish-type-tag--${dish.dishType.toLowerCase()}`">
                        {{ dishTypeMap[dish.dishType] || dish.dishType }}
                      </span>
                      <span v-else class="replace-tag replace-tag--auto">自动</span>
                    </td>
                    <template v-if="dish.compactGroup">
                      <td class="col-name col-name--compact">
                        <div class="replace-compact-list">
                          <div v-for="item in dish.items" :key="item.dishName" class="replace-compact-line">
                            {{ item.dishName }}
                          </div>
                        </div>
                      </td>
                      <td class="col-count col-count--compact">
                        <div class="replace-compact-list">
                          <div v-for="item in dish.items" :key="item.dishName" class="replace-compact-line">
                            {{ item.count }}
                          </div>
                        </div>
                      </td>
                      <td class="col-codes col-codes--compact">
                        <div class="replace-compact-list">
                          <div v-for="item in dish.items" :key="item.dishName" class="replace-compact-line">
                            {{ item.codeSnippet }}
                          </div>
                        </div>
                      </td>
                    </template>
                    <template v-else>
                      <td class="col-name">{{ dish.dishName }}</td>
                      <td class="col-count">{{ dish.count }}</td>
                      <td class="col-codes">{{ dish.codeSnippet }}</td>
                    </template>
                  </tr>
                </template>
                <tr v-if="manualReplaceDishes.length === 0 && autoReplaceRows.length === 0">
                  <td colspan="4" class="empty-row">暂无换菜记录</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 页脚统计 -->
      <div class="sheet-footer no-print">
        <div class="footer-stat">
          <span class="footer-stat__label">成功排餐</span>
          <span class="footer-stat__value footer-stat__value--success">{{ planData.successCount }}</span>
        </div>
        <div class="footer-stat">
          <span class="footer-stat__label">失败</span>
          <span class="footer-stat__value footer-stat__value--danger">{{ planData.failCount }}</span>
        </div>
        <div class="footer-stat">
          <span class="footer-stat__label">生成时间</span>
          <span class="footer-stat__value">{{ formatDateTime(planData.mealPlan.generateTime) }}</span>
        </div>
        <div class="footer-stat footer-stat--status">
          <el-tag :type="statusTag[planData.mealPlan.status] || 'info'" size="small">
            {{ statusMap[planData.mealPlan.status] || planData.mealPlan.status }}
          </el-tag>
        </div>
        <!-- 管理客户排餐按钮（非打印） -->
        <el-button
          type="primary"
          icon="el-icon-user"
          size="small"
          style="margin-left: 16px;"
          @click="openCustomerDialogForCurrent"
        >管理客户排餐</el-button>
      </div>
    </div>

    <!-- ════════════════════════════════════════
         弹窗区域
    ════════════════════════════════════════ -->

    <!-- 生成排餐计划对话框 -->
    <el-dialog title="生成排餐计划" :visible.sync="generateDialog.visible" width="420px" @close="resetGenerateForm">
      <el-form ref="generateForm" :model="generateForm" :rules="generateRules" label-width="100px">
        <el-form-item label="排餐日期" prop="date">
          <el-date-picker
            v-model="generateForm.date"
            type="date"
            placeholder="请选择排餐日期"
            value-format="yyyy-MM-dd"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="餐次" prop="mealType">
          <el-select v-model="generateForm.mealType" placeholder="请选择餐次" style="width: 100%;">
            <el-option label="早餐" value="BREAKFAST" />
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
          </el-select>
        </el-form-item>
        <el-form-item label="指定客户">
          <el-select
            v-model="generateForm.customerId"
            placeholder="不选则为全部客户"
            clearable
            filterable
            style="width: 100%;"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id"
              :label="`${customer.customerName} - ${customer.customerCode || '无编码'} - ${customer.phone || '无手机号'}`"
              :value="customer.id"
            >
              <span style="float: left">{{ customer.customerName }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px">
                {{ customer.customerCode || '无编码' }} | {{ customer.phone || '无手机号' }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-divider content-position="left">菜单模板槽位（选填）</el-divider>
        <el-form-item label="菜单周次" prop="menuWeekNum">
          <el-select v-model="generateForm.menuWeekNum" placeholder="不选则按日期推导" clearable style="width: 100%;">
            <el-option label="第 1 周" :value="1" />
            <el-option label="第 2 周" :value="2" />
            <el-option label="第 3 周" :value="3" />
            <el-option label="第 4 周" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单星期" prop="menuDayOfWeek">
          <el-select v-model="generateForm.menuDayOfWeek" placeholder="不选则按日期推导" clearable style="width: 100%;">
            <el-option label="周一" :value="1" />
            <el-option label="周二" :value="2" />
            <el-option label="周三" :value="3" />
            <el-option label="周四" :value="4" />
            <el-option label="周五" :value="5" />
            <el-option label="周六" :value="6" />
            <el-option label="周日" :value="7" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="generateDialog.visible = false">取 消</el-button>
        <el-button type="primary" :loading="generateDialog.loading" @click="handleGenerate">确认生成</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="排餐生产单展示规则"
      :visible.sync="rulesDialogVisible"
      width="880px"
      top="5vh"
    >
      <div class="rules-dialog-body">
        <div v-for="(section, index) in pageRuleSections" :key="`rule-${index}`" class="rules-section">
          <h3 class="rules-section__title">{{ section.title }}</h3>
          <p v-if="section.desc" class="rules-section__desc">{{ section.desc }}</p>
          <ul v-if="section.items && section.items.length > 0" class="rules-list">
            <li v-for="(item, itemIndex) in section.items" :key="`rule-item-${index}-${itemIndex}`">{{ item }}</li>
          </ul>
          <div v-if="section.examples && section.examples.length > 0" class="rules-example">
            <p v-for="(example, exampleIndex) in section.examples" :key="`rule-example-${index}-${exampleIndex}`">{{ example }}</p>
          </div>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button size="small" type="primary" @click="rulesDialogVisible = false">我知道了</el-button>
      </span>
    </el-dialog>

    <el-dialog
      title="换菜管理"
      :visible.sync="replaceDialogVisible"
      width="860px"
      :close-on-click-modal="false"
      top="5vh"
    >
      <div class="replace-dialog-body">
        <div class="replace-toolbar">
          <el-select
            v-model="replaceSelectedDishes"
            multiple
            filterable
            remote
            reserve-keyword
            placeholder="搜索菜品名称..."
            :remote-method="searchDishes"
            :loading="dishSearchLoading"
            style="width: 400px;"
            size="small"
            @change="onDishSelected"
          >
            <el-option
              v-for="dish in dishOptions"
              :key="dish.id"
              :label="`${dish.name}（${dishTypeMap[dish.dishType] || dish.dishType}）`"
              :value="dish.id"
            />
          </el-select>
          <el-button size="small" type="primary" icon="el-icon-plus" @click="addReplaceItem">添加换菜项</el-button>
        </div>

        <div v-if="replaceForm.length === 0" class="replace-empty">暂无换菜项，请搜索菜品并添加</div>

        <div v-for="(item, idx) in replaceForm" :key="`rf-${idx}`" class="replace-item-card">
          <div class="replace-item-header">
            <span class="replace-item-dish">
              <span class="dish-type-tag" :class="`dish-type-tag--${item.dishType.toLowerCase()}`">
                {{ dishTypeMap[item.dishType] || item.dishType }}
              </span>
              {{ item.dishName }}
            </span>
            <el-button type="text" icon="el-icon-delete" class="replace-item-del" @click="removeReplaceItem(idx)" />
          </div>
          <el-select
            v-model="item.customerPlanIds"
            multiple
            filterable
            placeholder="选择客户编号..."
            size="small"
            style="width: 100%;"
          >
            <el-option
              v-for="customer in planData.customers"
              :key="customer.id"
              :label="`${customer.customerCode || customer.customerName}（${customer.customerName}）`"
              :value="customer.id"
            />
          </el-select>
        </div>
      </div>

      <span slot="footer" class="dialog-footer">
        <el-button size="small" @click="replaceDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="replaceSaving" @click="handleSaveReplaces">保存</el-button>
      </span>
    </el-dialog>

    <!-- 客户排餐管理对话框 -->
    <el-dialog title="客户排餐管理" :visible.sync="customerDialog.visible" width="800px" @close="resetCustomerDialog">
      <div style="margin-bottom: 10px;">
        <el-button
          type="primary"
          icon="el-icon-check"
          size="small"
          :disabled="customerDialog.selections.length === 0 || !hasUnverifiedSelections()"
          @click="handleBatchVerify"
        >批量核销</el-button>
        <el-button
          type="danger"
          icon="el-icon-delete"
          size="small"
          :disabled="customerDialog.selections.length === 0"
          @click="handleBatchDeleteCustomers"
        >批量删除</el-button>
      </div>
      <el-table
        ref="customerDialogTable"
        v-loading="customerDialog.loading"
        :data="customerDialog.list"
        border
        size="small"
        @selection-change="handleCustomerSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="客户编号" prop="customerCode" align="center" min-width="120">
          <template slot-scope="scope">
            {{ scope.row.customerCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="核销状态" prop="isVerified" align="center" width="100">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.isVerified != null && scope.row.isVerified === 1" type="success" size="mini">已核销</el-tag>
            <el-tag v-else type="info" size="mini">未核销</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180">
          <template slot-scope="scope">
            <el-button
              v-if="!scope.row.isVerified || scope.row.isVerified !== 1"
              type="text"
              style="color: #67C23A;"
              @click="handleSingleVerify(scope.row)"
            >核销</el-button>
            <el-button
              type="text"
              :disabled="scope.row.isVerified === 1"
              :title="scope.row.isVerified === 1 ? '已核销，无法删除' : ''"
              :style="scope.row.isVerified === 1 ? 'color: #C0C4CC; cursor: not-allowed;' : 'color: #F56C6C;'"
              @click="handleDeleteSingleCustomer(scope.row)"
            >删除排餐</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="customerDialog.total > 0"
        :current-page="customerDialog.page"
        :page-sizes="[10, 20, 50, 100]"
        :page-size="customerDialog.size"
        :total="customerDialog.total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 15px; text-align: right;"
        @size-change="handleCustomerDialogSizeChange"
        @current-change="handleCustomerDialogPageChange"
      />
    </el-dialog>

    <!-- 核销确认对话框 -->
    <el-dialog title="核销确认" :visible.sync="verifyDialog.visible" width="500px">
      <div v-if="verifyDialog.records && verifyDialog.records.length > 0">
        <p>即将核销以下 {{ verifyDialog.records.length }} 个客户的排餐：</p>
        <el-table :data="verifyDialog.records" border size="mini" style="margin-top: 10px;">
          <el-table-column label="客户名称" prop="customerName" align="center" />
          <el-table-column label="核销餐数" align="center" width="100">
            <template>1</template>
          </el-table-column>
        </el-table>
        <p style="margin-top: 15px; color: #F56C6C;">核销后订单剩余餐数将减少1，已核销餐数将增加1</p>
      </div>
      <div slot="footer">
        <el-button @click="verifyDialog.visible = false">取 消</el-button>
        <el-button type="primary" :loading="verifyDialog.loading" @click="doVerify">确认核销</el-button>
      </div>
    </el-dialog>

    <!-- 客户详情对话框 -->
    <el-dialog title="客户详情" :visible.sync="addressDialog.visible" width="980px">
      <el-table
        v-loading="addressDialog.loading"
        :data="addressDialog.list"
        border
        size="small"
        max-height="500"
      >
        <el-table-column label="客户信息" align="left" min-width="260">
          <template slot-scope="scope">
            <div class="customer-contact-info">
              <div>联系人：{{ scope.row.customerCode || '-' }}</div>
              <div>电话：{{ scope.row.phone || '-' }}</div>
              <div>地址：{{ scope.row.addressDetail || '暂无地址' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="自定义菜单" align="center" width="100">
          <template slot-scope="scope">
            <el-image
              v-if="scope.row.customMenuImage"
              :src="getCustomMenuImageUrl(scope.row.customMenuImage)"
              :preview-src-list="[getCustomMenuImageUrl(scope.row.customMenuImage)]"
              fit="contain"
              style="width: 40px; height: 40px; cursor: pointer;"
            />
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="过敏信息" align="center" width="140">
          <template slot-scope="scope">
            <span v-if="scope.row.allergyTags && scope.row.allergyTags.length > 0">
              <el-tag v-for="tag in scope.row.allergyTags" :key="tag" type="danger" size="mini" style="margin-right: 3px;">{{ tag }}</el-tag>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="特殊要求" align="center" min-width="140">
          <template slot-scope="scope">
            <span v-if="scope.row.specialRequirements" class="ellipsis">{{ scope.row.specialRequirements }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="地址类型" prop="addressType" align="center" width="100">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.addressType" :type="scope.row.addressType === 'WEEKEND' ? 'warning' : (scope.row.addressType === 'WORKDAY' ? 'success' : 'info')" size="mini">
              {{ addressTypeMap[scope.row.addressType] || scope.row.addressType }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="addressDialog.total > 0"
        :current-page="addressDialog.page"
        :page-sizes="[10, 20, 50, 100]"
        :page-size="addressDialog.size"
        :total="addressDialog.total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 15px; text-align: right;"
        @size-change="handleAddressDialogSizeChange"
        @current-change="handleAddressDialogPageChange"
      />
    </el-dialog>
  </div>
</template>

<script>
import { getMealPlanList, getMealPlanFullDetail, getMealPlanCustomers, generateMealPlan, delMealPlan, delMealPlanCustomers, getMealPlanCustomerAddresses, getManualReplaces, saveManualReplaces } from '@/api/mealPlan'
import { getProfiles } from '@/api/customer/profile'
import { queryDishes } from '@/api/dish'
import { verifyMeal } from '@/api/mealVerification'
import { MealTypeName } from '@/utils/calendar'

export default {
  name: 'ScheduleRecord',
  data() {
    return {
      loading: false,
      planData: null,
      // 当前查询的 mealPlan 原始记录（用于删除）
      currentRecord: null,
      latestLoadRequestId: 0,
      baseApi: process.env.VUE_APP_BASE_API,

      queryDate: (() => {
        const d = new Date()
        const fmt = n => String(n).padStart(2, '0')
        const bd = new Date(d.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }))
        return `${bd.getFullYear()}-${fmt(bd.getMonth() + 1)}-${fmt(bd.getDate())}`
      })(),
      queryMealType: 'LUNCH',

      dishTypeMap: {
        MAIN: '主菜',
        SIDE: '副菜',
        SOUP: '汤品',
        VEGETABLE: '蔬菜',
        RICE: '米饭'
      },
      dishTypeOrder: { SOUP: 1, MAIN: 2, SIDE: 3, VEGETABLE: 4, RICE: 5 },
      missingTagToDishType: {
        无主菜: 'MAIN',
        无副菜: 'SIDE',
        无素菜: 'VEGETABLE',
        无米饭: 'RICE'
      },
      specialRequirementDishMap: {
        米饭: 'RICE',
        主菜: 'MAIN',
        副菜: 'SIDE',
        素菜: 'VEGETABLE'
      },
      statusMap: {
        SUCCESS: '成功',
        FAILED: '部分失败',
        GENERATING: '生成中'
      },
      statusTag: {
        SUCCESS: 'success',
        FAILED: 'danger',
        GENERATING: 'info'
      },

      // 生成排餐计划弹窗
      generateDialog: { visible: false, loading: false },
      generateForm: { date: null, mealType: 'LUNCH', customerId: null, menuWeekNum: null, menuDayOfWeek: null },
      customerOptions: [],
      generateRules: {
        date: [{ required: true, message: '请选择排餐日期', trigger: 'change' }],
        mealType: [{ required: true, message: '请选择餐次', trigger: 'change' }]
      },

      // 客户排餐管理弹窗
      customerDialog: {
        visible: false,
        loading: false,
        list: [],
        selections: [],
        currentRecord: null,
        page: 1,
        size: 10,
        total: 0
      },

      // 核销确认弹窗
      verifyDialog: {
        visible: false,
        loading: false,
        records: []
      },
      // 客户详情弹窗
      addressDialog: {
        visible: false,
        loading: false,
        list: [],
        page: 1,
        size: 10,
        total: 0
      },
      rulesDialogVisible: false,
      manualReplaces: [],
      replaceDialogVisible: false,
      replaceForm: [],
      dishOptions: [],
      dishSearchLoading: false,
      replaceSelectedDishes: [],
      replaceSaving: false,
      addressTypeMap: {
        DEFAULT: '默认地址',
        WORKDAY: '工作日地址',
        WEEKEND: '周末地址'
      },
      pageRuleSections: [
        {
          title: '编号区',
          items: [
            '首餐客户优先展示，普通客户排在后面。',
            '客户缺汤时，客户编号显示红色胶囊圈。',
            '客户是首餐时，编号右上角显示绿色“首”标记。',
            '黄色标签表示加菜或补充数量，如“加主菜×1”“加副菜×99”。',
            '浅红标签表示缺少某类菜品，如“无副菜”“无素菜”“无米饭”。',
            '特殊要求中能解析出主菜、副菜、素菜、米饭增减要求时，也会显示在编号区标签中。'
          ]
        },
        {
          title: '右上今日菜单汇总',
          items: [
            '按“类目 + 菜名”聚合展示汤品、主菜、副菜、蔬菜、米饭。',
            '人数表示当前实际吃该菜的客户数量。',
            '屏幕上的编号明细允许带运营说明信息。',
            '普通菜会汇总过敏过滤、自动换菜、缺菜、特殊要求和手工换菜提示。',
            '汤品额外包含缺汤客户编号。',
            '米饭会合并缺米饭、米饭替换、特殊要求以及米饭相关替换说明。'
          ]
        },
        {
          title: '右下换菜明细',
          items: [
            '分为手工换菜和自动换菜两部分。',
            '每一行按菜品类目和替换后的菜名聚合展示。',
            '第一列展示菜品类目，第二列展示替换后的菜名，第三列展示人数，第四列展示目标客户编号。',
            '米饭自动替换超过一种时只占一条米饭行，替换项目、人数和目标编号列内分别按行展示明细。'
          ]
        },
        {
          title: '打印规则',
          items: [
            '打印时隐藏操作栏、导航栏、标签栏等非打印内容。',
            '右上编号明细切换为打印专用编号列表。',
            '打印只显示客户编号，不显示“无素菜”“无副菜”“白米饭”“换菜:”等说明文字。',
            '打印编号会自动去重。'
          ],
          examples: [
            '示例：屏幕显示 “A002, B3301, A171, B2200, B2201 | 换菜: A002” 时，打印为 “A002, B3301, A171, B2200, B2201”。'
          ]
        }
      ]
    }
  },
  computed: {
    mealTypeText() {
      // 优先用已加载数据中的 mealType，其次用搜索框值
      const type = (this.planData && this.planData.mealPlan && this.planData.mealPlan.mealType) || this.queryMealType
      return MealTypeName[type] || type
    },
    showSupplementaryTags() {
      const type = (this.planData && this.planData.mealPlan && this.planData.mealPlan.mealType) || this.queryMealType
      return type !== 'BREAKFAST'
    },
    allCustomers() {
      if (!this.planData) return []
      const decorated = (this.planData.customers || []).map(c => ({
        ...c,
        isSoupMissing: this.isSoupMissing(c),
        specialRequirementTags: this.getSpecialRequirementTags(c),
        supplementaryTags: this.getSupplementaryTags(c)
      }))
      const firstCustomers = decorated.filter(item => item.firstMealOfOrder)
      const normalCustomers = decorated.filter(item => !item.firstMealOfOrder)
      return [...firstCustomers, ...normalCustomers]
    },
    regularDishes() {
      if (!this.planData) return []

      const allergyFilteredCodesByDishName = {}
      const customersWithoutSoup = new Set()
      const missingCodesByDishType = {}
      const specialRequirementCodesByDishType = {}
      const replacedCodesByOriginalDishName = {}
      const riceChangedDetailsByDisplayName = {}
      // 预先确定菜单米饭名称（从非替换的米饭项中取），确保所有米饭归为同一行
      let menuRiceName = null
      const customers = this.planData.customers || []
      customers.forEach(customer => {
        const items = customer.items || []
        items.forEach(item => {
          if (item.dishType === 'RICE' && !item.isReplaced && item.dishName && !menuRiceName) {
            menuRiceName = item.dishName
          }
        })
      })
      customers.forEach(customer => {
        const code = customer.customerCode || customer.customerName || ''
        const items = customer.items || []
        const specialRequirementTags = this.getSpecialRequirementTags(customer)
        specialRequirementTags.forEach(tag => {
          const dishType = this.getSpecialRequirementDishType(tag)
          if (!dishType || !code) return
          if (!specialRequirementCodesByDishType[dishType]) {
            specialRequirementCodesByDishType[dishType] = new Set()
          }
          specialRequirementCodesByDishType[dishType].add(`${code}(${tag})`)
        })
        if (this.isSoupMissing(customer) && code) {
          customersWithoutSoup.add(code)
        }
        const missingTags = this.getMissingDishTags(customer)
        missingTags.forEach(tag => {
          const dishType = this.missingTagToDishType[tag]
          if (!dishType || !code) return
          if (!missingCodesByDishType[dishType]) {
            missingCodesByDishType[dishType] = new Set()
          }
          missingCodesByDishType[dishType].add(`${code}(${tag})`)
        })
        items.forEach(item => {
          if (item.isReplaced && item.dishType !== 'RICE' && code) {
            const originalDishName = item.originalDishName || item.dishName
            if (originalDishName) {
              if (!replacedCodesByOriginalDishName[originalDishName]) {
                replacedCodesByOriginalDishName[originalDishName] = new Set()
              }
              replacedCodesByOriginalDishName[originalDishName].add(code)
            }
          }
          if (item.isReplaced && item.dishType === 'RICE' && code) {
            const riceKey = menuRiceName || item.originalDishName || item.dishName
            if (!riceChangedDetailsByDisplayName[riceKey]) {
              riceChangedDetailsByDisplayName[riceKey] = []
            }
            const detailGroups = riceChangedDetailsByDisplayName[riceKey]
            let detailGroup = detailGroups.find(group => group.dishName === item.dishName)
            if (!detailGroup) {
              detailGroup = { dishName: item.dishName, codes: [] }
              detailGroups.push(detailGroup)
            }
            detailGroup.codes.push(code)
          }
          if (item.isAllergyFiltered) {
            const filterDishName = item.dishType === 'RICE'
              ? (menuRiceName || item.originalDishName || item.dishName)
              : ((item.isReplaced && item.originalDishName) ? item.originalDishName : item.dishName)
            if (filterDishName) {
              if (!allergyFilteredCodesByDishName[filterDishName]) {
                allergyFilteredCodesByDishName[filterDishName] = new Set()
              }
              let displayText = code
              if (item.allergyReasons) {
                displayText = `${code}(${item.allergyReasons})`
              }
              allergyFilteredCodesByDishName[filterDishName].add(displayText)
            }
          }
        })
      })

      const groups = {}
      ;(this.planData.customers || []).forEach(customer => {
        const code = customer.customerCode || customer.customerName || ''
        ;(customer.items || []).filter(item => !item.isReplaced || item.dishType === 'RICE').forEach(item => {
          const displayName = item.dishType === 'RICE' ? (menuRiceName || item.originalDishName || item.dishName) : item.dishName
          const key = `${item.dishType}__${displayName}`
          if (!groups[key]) {
            groups[key] = { dishType: item.dishType, dishName: displayName, eatCodes: [] }
          }
          if (!item.isAllergyFiltered && (item.dishType !== 'RICE' || !item.isReplaced)) {
            if (!groups[key].eatCodes.includes(code)) {
              groups[key].eatCodes.push(code)
            }
          }
        })
      })

      return Object.values(groups)
        .sort((a, b) => (this.dishTypeOrder[a.dishType] || 99) - (this.dishTypeOrder[b.dishType] || 99))
        .map(g => {
          const excludedSet = allergyFilteredCodesByDishName[g.dishName]
          const excludedCodes = excludedSet ? Array.from(excludedSet) : []
          const replacedSet = replacedCodesByOriginalDishName[g.dishName]
          const replacedCodes = replacedSet ? Array.from(replacedSet) : []
          const missingCodesSet = missingCodesByDishType[g.dishType]
          const missingCodes = missingCodesSet ? Array.from(missingCodesSet) : []
          const specialRequirementSet = specialRequirementCodesByDishType[g.dishType]
          const specialRequirementCodes = specialRequirementSet ? Array.from(specialRequirementSet) : []
          const riceChangedDetails = riceChangedDetailsByDisplayName[g.dishName] || []
          const riceChangedCodes = this.buildRiceChangedCodeEntries(riceChangedDetails)
          const detailCodes = g.dishType === 'SOUP'
            ? Array.from(new Set([...excludedCodes, ...replacedCodes, ...customersWithoutSoup]))
            : g.dishType === 'RICE'
              ? this.mergeRiceCodeDetails(Array.from(new Set([...excludedCodes, ...missingCodes, ...riceChangedCodes, ...specialRequirementCodes])))
              : Array.from(new Set([...excludedCodes, ...replacedCodes, ...missingCodes, ...specialRequirementCodes]))
          const detailText = detailCodes.length > 0 ? this.buildFullCodeText(detailCodes) : '-'
          const manualCodes = this.manualCodesByDishType[g.dishType] || []
          const manualText = manualCodes.length > 0 ? `换菜: ${this.buildFullCodeText(manualCodes)}` : ''
          const printCodes = this.buildPrintCodeList(detailCodes, manualCodes)
          return {
            ...g,
            count: g.eatCodes.length,
            codeSnippet: manualText ? (detailText === '-' ? manualText : `${detailText} | ${manualText}`) : detailText,
            printCodeSnippet: printCodes.length > 0 ? this.buildFullCodeText(printCodes) : '-'
          }
        })
    },
    replacedDishes() {
      if (!this.planData) return []
      const groups = {}
      ;(this.planData.customers || []).forEach(customer => {
        const code = customer.customerCode || customer.customerName || ''
        ;(customer.items || []).filter(item => item.isReplaced).forEach(item => {
          const key = item.dishName
          if (!groups[key]) {
            groups[key] = {
              dishType: item.dishType,
              dishName: item.dishName,
              originalDishName: item.originalDishName,
              replaceReason: item.replaceReason,
              codes: []
            }
          }
          if (!groups[key].codes.includes(code)) {
            groups[key].codes.push(code)
          }
        })
      })
      return Object.values(groups).map(g => ({
        ...g,
        count: g.codes.length,
        codeSnippet: this.buildFullCodeText(g.codes)
      }))
    },
    autoReplaceRows() {
      const riceRows = this.replacedDishes.filter(item => item.dishType === 'RICE')
      const otherRows = this.replacedDishes.filter(item => item.dishType !== 'RICE')
      if (riceRows.length <= 1) {
        return this.replacedDishes
      }
      return [
        ...otherRows,
        {
          compactGroup: true,
          dishType: 'RICE',
          dishName: '米饭换菜',
          count: riceRows.reduce((sum, item) => sum + item.count, 0),
          items: riceRows
        }
      ]
    },
    manualCodesByDishType() {
      const map = {}
      this.manualReplaces.forEach(item => {
        if (!item.dishType || !item.customerCode) return
        if (!map[item.dishType]) {
          map[item.dishType] = new Set()
        }
        map[item.dishType].add(item.customerCode)
      })
      return Object.keys(map).reduce((result, type) => {
        result[type] = Array.from(map[type])
        return result
      }, {})
    },
    manualReplaceDishes() {
      const groups = {}
      this.manualReplaces.forEach(item => {
        const key = `${item.dishType}__${item.dishName}`
        if (!groups[key]) {
          groups[key] = {
            dishType: item.dishType,
            dishName: item.dishName,
            codes: []
          }
        }
        if (item.customerCode && !groups[key].codes.includes(item.customerCode)) {
          groups[key].codes.push(item.customerCode)
        }
      })
      return Object.values(groups)
        .sort((a, b) => (this.dishTypeOrder[a.dishType] || 99) - (this.dishTypeOrder[b.dishType] || 99))
        .map(item => ({
          ...item,
          count: item.codes.length,
          codeSnippet: this.buildFullCodeText(item.codes)
        }))
    }
  },
  created() {
    // 从 URL query 参数初始化（兼容从其他页面跳转过来带参的情况）
    const { mealPlanId, date, mealType } = this.$route.query
    if (date) this.queryDate = date
    if (mealType) this.queryMealType = mealType

    if (mealPlanId) {
      this.loadById(mealPlanId)
    } else if (date && mealType) {
      this.loadByDateAndMeal()
    }
  },
  methods: {
    // ─── 数据加载 ───────────────────────────────
    handleQuery() {
      this.loadByDateAndMeal()
    },
    loadById(id) {
      const requestId = ++this.latestLoadRequestId
      this.loading = true
      getMealPlanFullDetail(id).then(res => {
        if (requestId !== this.latestLoadRequestId) return
        this.planData = res
        this.currentRecord = res.mealPlan || null
        if (res.mealPlan) {
          this.queryDate = res.mealPlan.recordDate
          this.queryMealType = res.mealPlan.mealType
        }
        this.loadManualReplaces()
      }).catch(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.$message.error('加载排餐数据失败')
        this.manualReplaces = []
      }).finally(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.loading = false
      })
    },
    loadByDateAndMeal() {
      if (!this.queryDate || !this.queryMealType) {
        this.$message.warning('请选择日期和餐次')
        return
      }
      const requestId = ++this.latestLoadRequestId
      this.loading = true
      this.planData = null
      this.currentRecord = null
      getMealPlanList({ recordDate: this.queryDate, mealType: this.queryMealType, page: 0, size: 1 }).then(res => {
        if (requestId !== this.latestLoadRequestId) return null
        const list = res.content || []
        if (list.length === 0) {
          this.$message.warning('未找到该日期和餐次的排餐计划')
          this.manualReplaces = []
          return null
        }
        this.currentRecord = list[0]
        return getMealPlanFullDetail(list[0].id)
      }).then(res => {
        if (requestId !== this.latestLoadRequestId || !res) return
        this.planData = res
        this.loadManualReplaces()
      }).catch(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.$message.error('加载排餐数据失败')
        this.manualReplaces = []
      }).finally(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.loading = false
      })
    },

    // ─── 生成排餐计划 ────────────────────────────
    openGenerateDialog() {
      getProfiles({ page: 0, size: 9999 }).then(res => {
        this.customerOptions = res.content || []
      })
      // 预填当前查询日期和餐次
      this.generateForm.date = this.queryDate
      this.generateForm.mealType = this.queryMealType
      this.generateDialog.visible = true
    },
    resetGenerateForm() {
      this.$refs.generateForm && this.$refs.generateForm.resetFields()
      this.generateForm = { date: null, mealType: 'LUNCH', customerId: null, menuWeekNum: null, menuDayOfWeek: null }
    },
    handleGenerate() {
      this.$refs.generateForm.validate(valid => {
        if (!valid) return
        const { menuWeekNum, menuDayOfWeek } = this.generateForm
        if ((menuWeekNum != null) !== (menuDayOfWeek != null)) {
          this.$message.warning('菜单周次和菜单星期必须同时选择或同时不选')
          return
        }
        this.generateDialog.loading = true
        const data = { recordDate: this.generateForm.date, mealType: this.generateForm.mealType }
        if (this.generateForm.customerId) {
          data.customerId = this.generateForm.customerId
        }
        if (menuWeekNum != null) {
          data.menuWeekNum = menuWeekNum
          data.menuDayOfWeek = menuDayOfWeek
        }
        generateMealPlan(data)
          .then(() => {
            this.$message.success(`${this.generateForm.date} 排餐计划生成成功！`)
            this.generateDialog.visible = false
            // 自动切换查询到生成的日期/餐次
            this.queryDate = this.generateForm.date
            this.queryMealType = this.generateForm.mealType
            this.loadByDateAndMeal()
          })
          .catch(err => {
            const msg = err && err.response && err.response.data && err.response.data.message
            this.$message.error(msg || '生成排餐计划失败，请重试')
          })
          .finally(() => {
            this.generateDialog.loading = false
          })
      })
    },

    // ─── 删除排餐记录 ────────────────────────────
    handleDeleteCurrent() {
      if (!this.planData || !this.planData.mealPlan) return
      console.log('删除排餐记录')
      const mp = this.planData.mealPlan
      const mealLabel = MealTypeName[mp.mealType] || mp.mealType
      // 校验：存在已核销的客户记录时禁止删除整条计划
      const verifiedCustomers = (this.planData.customers || []).filter(c => c.isVerified === 1)
      if (verifiedCustomers.length > 0) {
        const names = verifiedCustomers.map(c => c.customerName).join('、')
        this.$message.error(`该排餐计划存在已核销的客户，无法删除。已核销客户：${names}`)
        return
      }
      this.$confirm(
        `确认删除 ${mp.recordDate} 的 ${mealLabel} 排餐计划吗？将同时删除相关的明细数据！`,
        '危险操作',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'error' }
      ).then(() => {
        delMealPlan({ recordDate: mp.recordDate, mealType: mp.mealType }).then(() => {
          this.$message.success('删除成功')
          this.planData = null
          this.currentRecord = null
        })
      }).catch(() => {})
    },

    // ─── 客户排餐管理 ────────────────────────────
    openCustomerDialogForCurrent() {
      if (!this.planData || !this.planData.mealPlan) return
      this.customerDialog.currentRecord = { id: this.planData.mealPlan.id, ...this.planData.mealPlan }
      this.customerDialog.page = 1
      this.customerDialog.visible = true
      this.fetchCustomerList()
    },
    /**
     * 分页加载客户排餐管理弹窗列表。
     */
    fetchCustomerList() {
      if (!this.customerDialog.currentRecord || !this.customerDialog.currentRecord.id) return
      this.customerDialog.loading = true
      getMealPlanCustomers(this.customerDialog.currentRecord.id, {
        page: this.customerDialog.page,
        size: this.customerDialog.size
      }).then(res => {
        const total = res.totalElements || 0
        const maxPage = Math.max(Math.ceil(total / this.customerDialog.size), 1)
        if (total > 0 && this.customerDialog.page > maxPage) {
          this.customerDialog.page = maxPage
          this.fetchCustomerList()
          return
        }
        this.customerDialog.list = res.content || []
        this.customerDialog.total = total
        this.customerDialog.selections = []
        this.$nextTick(() => {
          this.$refs.customerDialogTable && this.$refs.customerDialogTable.clearSelection && this.$refs.customerDialogTable.clearSelection()
        })
      }).catch(() => {
        this.customerDialog.list = []
        this.customerDialog.total = 0
      }).finally(() => {
        this.customerDialog.loading = false
      })
    },
    /**
     * 处理客户排餐管理弹窗分页大小变化。
     *
     * @param {number} size 每页条数
     */
    handleCustomerDialogSizeChange(size) {
      this.customerDialog.size = size
      this.customerDialog.page = 1
      this.fetchCustomerList()
    },
    /**
     * 处理客户排餐管理弹窗页码变化。
     *
     * @param {number} page 当前页码
     */
    handleCustomerDialogPageChange(page) {
      this.customerDialog.page = page
      this.fetchCustomerList()
    },
    handleCustomerSelectionChange(val) {
      this.customerDialog.selections = val
    },
    handleBatchDeleteCustomers() {
      console.log('123332')
      if (this.customerDialog.selections.length === 0) return
      // 过滤掉已核销的客户，只允许删除未核销的
      const toDelete = this.customerDialog.selections.filter(item => item.isVerified !== 1)
      if (toDelete.length === 0) {
        this.$message.warning('选中的客户均已核销，无法删除')
        return
      }
      const ids = toDelete.map(item => item.id)
      const skipCount = this.customerDialog.selections.length - toDelete.length
      const msg = skipCount > 0
        ? `选中的 ${this.customerDialog.selections.length} 个客户中有 ${skipCount} 个已核销，将只删除未核销的 ${ids.length} 个客户，确认继续？`
        : `确认删除选定的 ${ids.length} 个客户的排餐计划吗？将同时删除相关的明细数据！`
      this.$confirm(msg, '危险操作',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'error' }
      ).then(() => {
        this.doDeleteCustomers(ids)
      }).catch(() => {})
    },
    handleDeleteSingleCustomer(row) {
      // 校验：已核销的客户不允许删除
      if (row.isVerified === 1) {
        this.$message.warning(`客户 "${row.customerName}" 已核销，无法删除排餐计划`)
        return
      }
      this.$confirm(
        `确认删除客户 "${row.customerName}" 的排餐计划吗？将同时删除相关的明细数据！`,
        '危险操作',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'error' }
      ).then(() => {
        this.doDeleteCustomers([row.id])
      }).catch(() => {})
    },
    doDeleteCustomers(ids) {
      delMealPlanCustomers(ids).then(() => {
        this.$message.success('删除成功')
        this.fetchCustomerList()
        // 刷新生产单数据
        this.loadByDateAndMeal()
      }).catch(err => {
        const msg = err && err.response && err.response.data && err.response.data.message
        this.$message.error(msg || '删除失败，请重试')
      })
    },
    resetCustomerDialog() {
      this.customerDialog.list = []
      this.customerDialog.selections = []
      this.customerDialog.currentRecord = null
      this.customerDialog.page = 1
      this.customerDialog.size = 10
      this.customerDialog.total = 0
    },
    hasUnverifiedSelections() {
      return this.customerDialog.selections.some(item => item.isVerified !== 1)
    },

    // ─── 核销 ────────────────────────────────────
    handleSingleVerify(row) {
      this.verifyDialog.records = [row]
      this.verifyDialog.visible = true
    },
    handleBatchVerify() {
      const unverified = this.customerDialog.selections.filter(item => item.isVerified !== 1)
      if (unverified.length === 0) {
        this.$message.warning('请选择未核销的客户')
        return
      }
      this.verifyDialog.records = unverified
      this.verifyDialog.visible = true
    },
    doVerify() {
      const ids = this.verifyDialog.records.map(item => item.id)
      this.verifyDialog.loading = true
      verifyMeal({ customerPlanIds: ids, remark: '' })
        .then(res => {
          if (res.failCount > 0) {
            this.$message.warning(`核销完成：成功 ${res.successCount} 个，失败 ${res.failCount} 个`)
            if (res.failReasons && res.failReasons.length > 0) {
              this.$message.error(res.failReasons.join('\n'))
            }
          } else {
            this.$message.success(`核销成功，共核销 ${res.successCount} 个客户`)
          }
          this.verifyDialog.visible = false
          this.fetchCustomerList()
          this.loadByDateAndMeal()
        })
        .catch(err => {
          const msg = err && err.response && err.response.data && err.response.data.message
          this.$message.error(msg || '核销失败，请重试')
        })
        .finally(() => {
          this.verifyDialog.loading = false
        })
    },

    /**
     * 获取自定义菜单图片完整访问地址。
     *
     * @param {string} path 图片相对路径或绝对地址
     * @returns {string} 可直接访问的图片地址
     */
    getCustomMenuImageUrl(path) {
      if (!path) return ''
      if (path.startsWith('http://') || path.startsWith('https://')) return path
      return this.baseApi + path
    },
    /**
     * 分页加载客户详情弹窗列表。
     */
    fetchAddressDialogList() {
      if (!this.planData || !this.planData.mealPlan) return
      this.addressDialog.loading = true
      getMealPlanCustomerAddresses(this.planData.mealPlan.id, {
        page: this.addressDialog.page,
        size: this.addressDialog.size
      }).then(res => {
        this.addressDialog.list = res.content || []
        this.addressDialog.total = res.totalElements || 0
      }).catch(() => {
        this.addressDialog.list = []
        this.addressDialog.total = 0
        this.$message.error('获取客户详情失败')
      }).finally(() => {
        this.addressDialog.loading = false
      })
    },
    /**
     * 处理客户详情弹窗分页大小变化。
     *
     * @param {number} size 每页条数
     */
    handleAddressDialogSizeChange(size) {
      this.addressDialog.size = size
      this.addressDialog.page = 1
      this.fetchAddressDialogList()
    },
    /**
     * 处理客户详情弹窗页码变化。
     *
     * @param {number} page 当前页码
     */
    handleAddressDialogPageChange(page) {
      this.addressDialog.page = page
      this.fetchAddressDialogList()
    },
    // ─── 客户详情查询 ──────────────────────────────
    openAddressDialog() {
      if (!this.planData || !this.planData.mealPlan) return
      this.addressDialog.visible = true
      this.addressDialog.page = 1
      this.addressDialog.list = []
      this.addressDialog.total = 0
      this.fetchAddressDialogList()
    },
    loadManualReplaces() {
      if (!this.planData || !this.planData.mealPlan) {
        this.manualReplaces = []
        return
      }
      getManualReplaces(this.planData.mealPlan.id).then(res => {
        this.manualReplaces = res || []
      }).catch(() => {
        this.manualReplaces = []
      })
    },
    openReplaceDialog() {
      const grouped = {}
      this.manualReplaces.forEach(item => {
        const key = `${item.dishId}__${item.dishType}`
        if (!grouped[key]) {
          grouped[key] = {
            dishId: item.dishId,
            dishName: item.dishName,
            dishType: item.dishType,
            customerPlanIds: []
          }
        }
        grouped[key].customerPlanIds.push(item.customerPlanId)
      })
      this.replaceForm = Object.values(grouped)
      this.replaceSelectedDishes = this.replaceForm.map(item => item.dishId)
      this.dishOptions = this.replaceForm.map(item => ({
        id: item.dishId,
        name: item.dishName,
        dishType: item.dishType
      }))
      this.replaceDialogVisible = true
    },
    searchDishes(query) {
      if (!query) {
        this.dishOptions = []
        return
      }
      this.dishSearchLoading = true
      queryDishes({ name: query, enabled: true, page: 0, size: 20 }).then(res => {
        this.dishOptions = res.content || []
      }).catch(() => {
        this.dishOptions = []
      }).finally(() => {
        this.dishSearchLoading = false
      })
    },
    onDishSelected(selectedIds) {
      const existingIds = new Set(this.replaceForm.map(item => item.dishId))
      selectedIds.forEach(id => {
        if (existingIds.has(id)) return
        const dish = this.dishOptions.find(option => option.id === id)
        if (!dish) return
        this.replaceForm.push({
          dishId: dish.id,
          dishName: dish.name,
          dishType: dish.dishType,
          customerPlanIds: []
        })
      })
      this.replaceForm = this.replaceForm.filter(item => selectedIds.includes(item.dishId))
    },
    addReplaceItem() {
      if (this.replaceSelectedDishes.length === 0) {
        this.$message.warning('请先搜索并选择菜品')
      }
    },
    removeReplaceItem(index) {
      const removed = this.replaceForm.splice(index, 1)[0]
      if (!removed) return
      this.replaceSelectedDishes = this.replaceSelectedDishes.filter(id => id !== removed.dishId)
    },
    handleSaveReplaces() {
      if (!this.planData || !this.planData.mealPlan) return
      const items = this.replaceForm
        .filter(item => Array.isArray(item.customerPlanIds) && item.customerPlanIds.length > 0)
        .map(item => ({
          dishId: item.dishId,
          dishType: item.dishType,
          customerPlanIds: item.customerPlanIds
        }))
      this.replaceSaving = true
      saveManualReplaces(this.planData.mealPlan.id, { items }).then(() => {
        this.$message.success('保存成功')
        this.replaceDialogVisible = false
        this.loadManualReplaces()
      }).catch(() => {
        this.$message.error('保存失败')
      }).finally(() => {
        this.replaceSaving = false
      })
    },

    // ─── 工具方法 ─────────────────────────────────
    isSoupMissing(customer) {
      const dishTypes = (customer.items || []).map(item => item.dishType)
      return !dishTypes.includes('SOUP') && customer.includeSoup !== 1
    },
    /**
     * 生成生产日期标记的悬浮提示文案。
     *
     * @param {Object} customer 客户排餐信息
     * @returns {string} 标记提示文案
     */
    getProductionDateBadgeTip(customer) {
      const days = Number(customer.productionDateDiffDays)
      if (days === 0) {
        return '生产当天'
      }
      if (days > 0) {
        return `生产后第${days}天`
      }
      return '临近生产日期'
    },
    getRiceRequirement(customer) {
      const tags = this.getSpecialRequirementTags(customer)
      return tags.find(tag => this.getSpecialRequirementDishType(tag) === 'RICE' && tag.startsWith('加')) || ''
    },
    getSpecialRequirementTags(customer) {
      const req = customer.specialRequirements
      if (!req) return []
      const matches = []
      const addReg = /加\s*(?:\d+\s*份\s*)?(米饭|主菜|副菜|素菜)/g
      const removeReg = /(?:不要|不加|去掉|免)\s*(米饭|主菜|副菜|素菜)/g
      this.collectSpecialRequirementMatches(req, removeReg, matches)
      this.collectSpecialRequirementMatches(req, addReg, matches)
      const tags = []
      matches
        .sort((a, b) => a.index - b.index)
        .forEach(item => {
          if (item.tag && !tags.includes(item.tag)) {
            tags.push(item.tag)
          }
        })
      return tags
    },
    collectSpecialRequirementMatches(req, reg, matches) {
      let match = reg.exec(req)
      while (match) {
        const tag = match[0].trim()
        if (tag) {
          matches.push({ tag, index: match.index })
        }
        match = reg.exec(req)
      }
    },
    getSpecialRequirementDishType(tag) {
      if (!tag) return null
      const dishName = Object.keys(this.specialRequirementDishMap).find(name => tag.indexOf(name) !== -1)
      return dishName ? this.specialRequirementDishMap[dishName] : null
    },
    getSupplementaryTags(customer) {
      const missingTags = this.getMissingDishTags(customer)
      const addTags = []

      // 加菜标签
      if (customer.supplementaryMainCount > 0) {
        addTags.push(`加主菜×${customer.supplementaryMainCount}`)
      }
      if (customer.supplementaryRiceCount > 0) {
        addTags.push(`加米饭×${customer.supplementaryRiceCount}`)
      }
      if (customer.supplementarySideCount > 0) {
        addTags.push(`加副菜×${customer.supplementarySideCount}`)
      }
      if (customer.supplementarySoupCount > 0) {
        addTags.push(`加汤×${customer.supplementarySoupCount}`)
      }
      if (customer.supplementaryVegCount > 0) {
        addTags.push(`加素菜×${customer.supplementaryVegCount}`)
      }

      // 将"无菜"标签放在前面，"加菜"标签放在后面
      return [...missingTags, ...addTags]
    },
    getMissingDishTags(customer) {
      const missingTags = []
      const dishTypes = (customer.items || []).map(item => item.dishType)
      if (!dishTypes.includes('MAIN')) {
        missingTags.push('无主菜')
      }
      if (!dishTypes.includes('SIDE')) {
        missingTags.push('无副菜')
      }
      if (!dishTypes.includes('VEGETABLE')) {
        missingTags.push('无素菜')
      }
      if (!dishTypes.includes('RICE') && customer.includeRice !== 1) {
        missingTags.push('无米饭')
      }
      return missingTags
    },
    buildFullCodeText(codes) {
      if (!codes || codes.length === 0) return '-'
      return codes.join(', ')
    },
    buildPrintCodeList(detailCodes, manualCodes) {
      const ordered = []
      const seen = new Set()
      ;[...(detailCodes || []), ...(manualCodes || [])].forEach(entry => {
        const code = this.extractCustomerCode(entry)
        if (!code || seen.has(code)) return
        seen.add(code)
        ordered.push(code)
      })
      return ordered
    },
    extractCustomerCode(entry) {
      if (!entry) return ''
      const text = String(entry).trim()
      if (!text) return ''
      const match = text.match(/^[^,(|，\s]+/)
      return match ? match[0] : ''
    },
    buildRiceChangedCodeEntries(detailGroups) {
      if (!detailGroups || detailGroups.length === 0) return []
      return detailGroups.reduce((entries, group) => {
        if (!group.codes || group.codes.length === 0) return entries
        group.codes.forEach(code => {
          entries.push(`${code}(${group.dishName})`)
        })
        return entries
      }, [])
    },
    mergeRiceCodeDetails(entries) {
      if (!entries || entries.length === 0) return []
      const mergedByCode = {}
      const codeOrder = {}
      let orderCounter = 0
      const passthroughEntries = []
      const entryReg = /^([^(),\s]+)((?:\([^)]*\))*)$/

      entries.forEach(entry => {
        const text = (entry || '').trim()
        if (!text) return
        const match = text.match(entryReg)
        if (!match) {
          passthroughEntries.push(text)
          return
        }
        const code = match[1]
        const tagPart = match[2] || ''
        if (!mergedByCode[code]) {
          mergedByCode[code] = []
          codeOrder[code] = orderCounter++
        }
        const tagMatches = tagPart.match(/\(([^)]*)\)/g) || []
        tagMatches.forEach(tagChunk => {
          const tagText = tagChunk.substring(1, tagChunk.length - 1)
          if (tagText && !mergedByCode[code].includes(tagText)) {
            mergedByCode[code].push(tagText)
          }
        })
      })

      const singleTagGroups = {}
      const outputWithOrder = []
      Object.keys(mergedByCode).forEach(code => {
        const tags = mergedByCode[code] || []
        const idx = codeOrder[code]
        if (tags.length <= 0) {
          outputWithOrder.push({ idx, text: code })
          return
        }
        if (tags.length > 1) {
          outputWithOrder.push({ idx, text: `${code}${tags.map(tag => `(${tag})`).join('')}` })
          return
        }
        const singleTag = tags[0]
        if (!singleTagGroups[singleTag]) {
          singleTagGroups[singleTag] = []
        }
        singleTagGroups[singleTag].push({ code, idx })
      })

      Object.keys(singleTagGroups).forEach(tag => {
        const rows = singleTagGroups[tag].sort((a, b) => a.idx - b.idx)
        const idx = rows[0].idx
        if (tag === '白米饭') {
          const mergedCodes = rows.map(item => item.code).join(', ')
          outputWithOrder.push({ idx, text: `${mergedCodes}(白米饭)` })
          return
        }
        if (rows.length === 1) {
          outputWithOrder.push({ idx, text: `${rows[0].code}(${tag})` })
          return
        }
        const mergedCodes = rows.map(item => item.code).join(', ')
        outputWithOrder.push({ idx, text: `${mergedCodes}(${tag})` })
      })

      const mergedEntries = outputWithOrder
        .sort((a, b) => a.idx - b.idx)
        .map(item => item.text)
      return [...mergedEntries, ...passthroughEntries]
    },
    formatDate(dateStr) {
      if (!dateStr) return '-'
      const parts = dateStr.split('-')
      if (parts.length >= 3) return `${parseInt(parts[1])}.${parseInt(parts[2])}`
      return dateStr
    },
    formatDateTime(dt) {
      if (!dt) return '-'
      return dt.replace('T', ' ').substring(0, 16)
    },
    handlePrint() {
      window.print()
    }
  }
}
</script>

<style scoped>
/* ──────────────────────────────────────────
   基础
────────────────────────────────────────── */
.production-sheet-wrapper {
  min-height: 100vh;
  background: #f8f9fb;
  font-family: 'Manrope', 'Inter', 'PingFang SC', sans-serif;
  padding-bottom: 40px;
}

/* ──────────────────────────────────────────
   顶部操作栏
────────────────────────────────────────── */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 32px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid #e2e8f0;
  position: sticky;
  top: 0;
  z-index: 100;
  flex-wrap: wrap;
  gap: 8px;
}
.action-bar__title {
  font-size: 18px;
  font-weight: 800;
  color: #006b5c;
  letter-spacing: -0.5px;
}
.action-bar__right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

/* ──────────────────────────────────────────
   空/加载状态
────────────────────────────────────────── */
.loading-mask {
  height: 200px;
}
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #909399;
}
.empty-state i {
  font-size: 52px;
  display: block;
  margin-bottom: 12px;
  color: #c0c4cc;
}

/* ──────────────────────────────────────────
   生产单主体卡片
────────────────────────────────────────── */
.sheet-root {
  max-width: 1400px;
  margin: 28px auto;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 24px -2px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

/* 页头 */
.sheet-header {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  border-bottom: 1px solid #e2e8f0;
}
.sheet-header__cell {
  padding: 20px 28px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-right: 1px solid #e2e8f0;
}
.sheet-header__cell:last-child {
  border-right: none;
  justify-content: flex-end;
}
.cell-label {
  font-size: 11px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #94a3b8;
}
.cell-value {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}
.cell-value--hero {
  font-size: 36px;
  font-weight: 900;
  color: #006b5c;
  letter-spacing: -1px;
  line-height: 1;
}

/* 主体两栏 */
.sheet-body {
  display: flex;
  min-height: 600px;
}
.sheet-left {
  width: 46%;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 18px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.section-title > span:first-child {
  font-size: 10px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #64748b;
}
.section-badge {
  font-size: 9px;
  font-weight: 700;
  background: #fef3c7;
  color: #92400e;
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.06em;
}
.code-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  flex: 1;
}
.code-cell {
  padding: 14px 12px 12px;
  min-height: 68px;
  border-bottom: 1px solid #f1f5f9;
  border-right: 1px solid #f1f5f9;
  position: relative;
  transition: background 0.15s;
}
.code-cell:nth-child(4n) { border-right: none; }
.code-cell:hover { background: #f8fffe; }
.code-text {
  font-size: 13px;
  font-weight: 700;
  color: #475569;
  display: block;
}
.code-text--tooltip {
  cursor: pointer;
}
.code-main {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
}
.code-badges {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.code-first-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid #86efac;
  background: #dcfce7;
  color: #166534;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}
.code-production-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid #fdba74;
  background: #ffedd5;
  color: #9a3412;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}
.code-text--soup-missing {
  display: inline-block;
  padding: 2px 10px;
  border: 2px solid #ef4444;
  border-radius: 999px;
  background: rgba(254, 226, 226, 0.45);
  color: #b91c1c;
}
/* 加菜标签 */
.supplementary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}
.supplementary-tag {
  display: inline-block;
  padding: 2px 6px;
  background: #fef3c7;
  color: #92400e;
  font-size: 10px;
  font-weight: 600;
  border-radius: 3px;
  border: 1px solid #fcd34d;
  letter-spacing: 0.02em;
}
.supplementary-tag--missing {
  background: #fee2e2;
  color: #991b1b;
  border-color: #fca5a5;
}
.rice-requirement-tag {
  margin-top: 4px;
  padding: 2px 6px;
  background: #dbeafe;
  color: #1e40af;
  font-size: 10px;
  font-weight: 600;
  border-radius: 4px;
  display: inline-block;
}

/* 右栏 */
.sheet-right {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.sheet-right__top {
  flex: 1;
  border-bottom: 1px solid #e2e8f0;
}
.sheet-divider { height: 0; }
.sheet-right__bottom { flex: 1; }

/* 菜单表格 */
.dish-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  height: 100%;
}
.dish-table thead tr {
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.dish-table th {
  padding: 10px 14px;
  font-size: 10px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #64748b;
  text-align: left;
}
.dish-table th.col-count,
.dish-table td.col-count { text-align: center; width: 68px; }
.dish-table th.col-category,
.dish-table td.col-category { width: 74px; text-align: center; border-right: 1px solid #f1f5f9; }
.dish-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #f8fafc;
  color: #334155;
  vertical-align: middle;
}
.dish-table tr:nth-child(even) td { background: rgba(248, 250, 252, 0.4); }
.dish-table tr:hover td { background: #f0fdf9; }
.col-name { font-weight: 600; color: #1e293b; width: 20%; }
.col-count { font-weight: 700; color: #006b5c; font-size: 15px; }
.col-codes {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.6;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.codes-print {
  display: none;
}
.empty-row { text-align: center; color: #c0c4cc; font-style: italic; padding: 20px 0; }

/* 菜品类型标签 */
.dish-type-tag {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
}
.dish-type-tag--soup      { background: #e0f2fe; color: #0369a1; }
.dish-type-tag--main      { background: #fee2e2; color: #991b1b; }
.dish-type-tag--side      { background: #fef3c7; color: #92400e; }
.dish-type-tag--vegetable { background: #dcfce7; color: #166534; }
.dish-type-tag--rice      { background: #f3f4f6; color: #374151; }
.replace-tag {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
}
.replace-tag--manual { background: #fef3c7; color: #92400e; }
.replace-tag--auto { background: #fee2e2; color: #991b1b; }
.dish-table tr.replace-compact-row td {
  vertical-align: top;
}
.col-name--compact {
  padding-top: 8px;
  padding-bottom: 8px;
}
.col-count--compact,
.col-codes--compact {
  padding-top: 8px;
  padding-bottom: 8px;
}
.replace-compact-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.replace-compact-line {
  min-height: 22px;
  line-height: 22px;
  font-size: 11px;
  font-weight: 500;
}
.replace-compact-line + .replace-compact-line {
  padding-top: 6px;
  border-top: 1px solid #e2e8f0;
}
.col-codes--compact .replace-compact-line {
  line-height: 1.5;
  overflow-wrap: anywhere;
  word-break: break-word;
}

/* 换菜管理弹窗 */
.replace-dialog-body {
  max-height: 60vh;
  overflow-y: auto;
}
.replace-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.replace-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
  font-style: italic;
}
.replace-item-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #fafbfc;
}
.replace-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.replace-item-dish {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}
.replace-item-del {
  color: #f56c6c;
}

.rules-dialog-body {
  max-height: 68vh;
  overflow-y: auto;
  padding-right: 8px;
}
.rules-section + .rules-section {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #ebeef5;
}
.rules-section__title {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}
.rules-section__desc {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}
.rules-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.8;
}
.rules-list li + li {
  margin-top: 4px;
}
.rules-example {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}
.rules-example p {
  margin: 0;
}

/* 页脚 */
.sheet-footer {
  display: flex;
  align-items: center;
  gap: 36px;
  padding: 16px 28px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
  flex-wrap: wrap;
}
.footer-stat { display: flex; flex-direction: column; gap: 2px; }
.footer-stat--status { margin-left: auto; }
.footer-stat__label {
  font-size: 9px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #94a3b8;
}
.footer-stat__value { font-size: 15px; font-weight: 700; color: #1e293b; }
.footer-stat__value--success { color: #16a34a; }
.footer-stat__value--danger  { color: #dc2626; }

.customer-contact-info {
  line-height: 1.8;
  text-align: left;
  white-space: normal;
  word-break: break-all;
}

/* ──────────────────────────────────────────
   打印
────────────────────────────────────────── */
@media print {
  .no-print { display: none !important; }
  .production-sheet-wrapper { background: #fff; padding: 0; }
  .sheet-root {
    margin: 0;
    border: none;
    box-shadow: none;
    border-radius: 0;
    max-width: 100%;
  }
  .code-cell { min-height: 60px; padding: 12px 10px; }
  .code-main { gap: 4px; }
  .code-first-badge {
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    font-size: 9px;
    font-weight: 700;
    border-radius: 4px;
    top: 8px;
    right: 8px;
    line-height: 1;
  }
  .dish-table tr:hover td { background: initial; }
  .dish-table tr:nth-child(even) td { background: rgba(0,0,0,0.02); }
  body { font-size: 12px; }
  .codes-screen { display: none !important; }
  .codes-print { display: inline !important; }
  .ellipsis {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }
}
</style>

<!-- 全局打印样式：隐藏 eladmin Layout 框架 -->
<style>
@media print {
  .navbar, .app-header-wrapper, .el-header { display: none !important; }
  .tags-view-container, .tagsView-container, .tags-view-wrapper { display: none !important; }
  .sidebar-container, .side-bar, .el-aside { display: none !important; }
  .footer, .el-footer, .app-footer { display: none !important; }
  .main-container, .app-main, .el-main {
    margin-left: 0 !important;
    padding: 0 !important;
    width: 100% !important;
  }
  .app-wrapper, #app {
    padding: 0 !important;
    margin: 0 !important;
    width: 100% !important;
  }
}
</style>
