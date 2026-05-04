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
        <!-- 查询客户配送地址 -->
        <el-button
          size="small"
          type="info"
          icon="el-icon-location"
          :disabled="!planData"
          @click="openAddressDialog"
        >配送地址</el-button>
        <!-- 打印 -->
        <el-button size="small" icon="el-icon-printer" @click="handlePrint">打印预览</el-button>
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
                  <td class="col-codes">{{ dish.codeSnippet }}</td>
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
                <template v-if="replacedDishes.length > 0">
                  <tr v-for="(dish, idx) in replacedDishes" :key="`rep-${idx}`">
                    <td v-if="idx === 0" class="col-category" :rowspan="replacedDishes.length">明细</td>
                    <td class="col-name">{{ dish.dishName }}</td>
                    <td class="col-count">{{ dish.count }}</td>
                    <td class="col-codes">{{ dish.codeSnippet }}</td>
                  </tr>
                </template>
                <tr v-else>
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
        v-loading="customerDialog.loading"
        :data="customerDialog.list"
        border
        size="small"
        @selection-change="handleCustomerSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="客户" prop="customerName" align="center" min-width="120" />
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

    <!-- 客户配送地址对话框 -->
    <el-dialog title="客户配送地址" :visible.sync="addressDialog.visible" width="800px">
      <el-table
        v-loading="addressDialog.loading"
        :data="addressDialog.list"
        border
        size="small"
        max-height="500"
      >
        <el-table-column label="客户编号" prop="customerCode" align="center" width="120" />
        <el-table-column label="手机号" prop="phone" align="center" width="120" />
        <el-table-column label="配送地址" prop="addressDetail" align="center" min-width="160">
          <template slot-scope="scope">
            {{ scope.row.addressDetail || '暂无地址' }}
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
    </el-dialog>
  </div>
</template>

<script>
import { getMealPlanList, getMealPlanFullDetail, generateMealPlan, delMealPlan, delMealPlanCustomers, getMealPlanCustomerAddresses } from '@/api/mealPlan'
import { getProfiles } from '@/api/customer/profile'
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
        currentRecord: null
      },

      // 核销确认弹窗
      verifyDialog: {
        visible: false,
        loading: false,
        records: []
      },
      // 客户配送地址弹窗
      addressDialog: {
        visible: false,
        loading: false,
        list: []
      },
      addressTypeMap: {
        DEFAULT: '默认地址',
        WORKDAY: '工作日地址',
        WEEKEND: '周末地址'
      }
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
      return (this.planData.customers || []).map(c => ({
        ...c,
        isSoupMissing: this.isSoupMissing(c),
        supplementaryTags: this.getSupplementaryTags(c)
      }))
    },
    regularDishes() {
      if (!this.planData) return []

      const allergyFilteredCodesByDishName = {}
      const customersWithoutSoup = new Set()
      const riceChangedDetailsByOriginalDishName = {}
      ;(this.planData.customers || []).forEach(customer => {
        const code = customer.customerCode || customer.customerName || ''
        if (this.isSoupMissing(customer) && code) {
          customersWithoutSoup.add(code)
        }
        (customer.items || []).forEach(item => {
          if (item.isReplaced && item.dishType === 'RICE' && item.originalDishName && code) {
            if (!riceChangedDetailsByOriginalDishName[item.originalDishName]) {
              riceChangedDetailsByOriginalDishName[item.originalDishName] = []
            }
            const detailGroups = riceChangedDetailsByOriginalDishName[item.originalDishName]
            let detailGroup = detailGroups.find(group => group.dishName === item.dishName)
            if (!detailGroup) {
              detailGroup = { dishName: item.dishName, codes: [] }
              detailGroups.push(detailGroup)
            }
            detailGroup.codes.push(code)
          }
          if (item.isAllergyFiltered) {
            const filterDishName = (item.isReplaced && item.originalDishName) ? item.originalDishName : item.dishName
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
          const displayName = item.dishType === 'RICE' ? (item.originalDishName || item.dishName) : item.dishName
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
          const riceChangedDetails = riceChangedDetailsByOriginalDishName[g.dishName] || []
          const riceChangedText = this.buildRiceChangedCodeText(riceChangedDetails)
          const detailCodes = g.dishType === 'SOUP'
            ? Array.from(new Set([...excludedCodes, ...customersWithoutSoup]))
            : g.dishType === 'RICE'
              ? [...excludedCodes, ...(riceChangedText ? [riceChangedText] : [])]
              : excludedCodes
          return {
            ...g,
            count: g.eatCodes.length,
            codeSnippet: detailCodes.length > 0 ? this.buildFullCodeText(detailCodes) : '-'
          }
        })
    },
    replacedDishes() {
      if (!this.planData) return []
      const groups = {}
      ;(this.planData.customers || []).forEach(customer => {
        const code = customer.customerCode || customer.customerName || ''
        ;(customer.items || []).filter(item => item.isReplaced && item.dishType !== 'RICE').forEach(item => {
          const key = item.dishName
          if (!groups[key]) {
            groups[key] = { dishName: item.dishName, originalDishName: item.originalDishName, replaceReason: item.replaceReason, codes: [] }
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
      }).catch(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.$message.error('加载排餐数据失败')
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
          return null
        }
        this.currentRecord = list[0]
        return getMealPlanFullDetail(list[0].id)
      }).then(res => {
        if (requestId !== this.latestLoadRequestId || !res) return
        this.planData = res
      }).catch(() => {
        if (requestId !== this.latestLoadRequestId) return
        this.$message.error('加载排餐数据失败')
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
      this.customerDialog.visible = true
      this.fetchCustomerList()
    },
    fetchCustomerList() {
      this.customerDialog.loading = true
      getMealPlanFullDetail(this.customerDialog.currentRecord.id).then(res => {
        this.customerDialog.list = res.customers || []
        this.customerDialog.loading = false
      }).catch(() => {
        this.customerDialog.loading = false
      })
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

    // ─── 配送地址查询 ──────────────────────────────
    openAddressDialog() {
      if (!this.planData || !this.planData.mealPlan) return
      this.addressDialog.visible = true
      this.addressDialog.loading = true
      this.addressDialog.list = []
      getMealPlanCustomerAddresses(this.planData.mealPlan.id).then(res => {
        this.addressDialog.list = res || []
      }).catch(() => {
        this.$message.error('获取配送地址失败')
      }).finally(() => {
        this.addressDialog.loading = false
      })
    },

    // ─── 工具方法 ─────────────────────────────────
    isSoupMissing(customer) {
      const dishTypes = (customer.items || []).map(item => item.dishType)
      return !dishTypes.includes('SOUP') && customer.includeSoup !== 1
    },
    getSupplementaryTags(customer) {
      const missingTags = []
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

      // 无菜品标签
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

      // 将"无菜"标签放在前面，"加菜"标签放在后面
      return [...missingTags, ...addTags]
    },
    buildFullCodeText(codes) {
      if (!codes || codes.length === 0) return '-'
      return codes.join(', ')
    },
    buildRiceChangedCodeText(detailGroups) {
      if (!detailGroups || detailGroups.length === 0) return ''
      return detailGroups.map(group => {
        if (!group.codes || group.codes.length === 0) return ''
        if (group.codes.length === 1) {
          return `${group.codes[0]}(${group.dishName})`
        }
        const leadingCodes = group.codes.slice(0, -1)
        const lastCode = group.codes[group.codes.length - 1]
        return `${this.buildFullCodeText(leadingCodes)}, ${lastCode}(${group.dishName})`
      }).filter(Boolean).join(', ')
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
  .dish-table tr:hover td { background: initial; }
  .dish-table tr:nth-child(even) td { background: rgba(0,0,0,0.02); }
  body { font-size: 12px; }
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
