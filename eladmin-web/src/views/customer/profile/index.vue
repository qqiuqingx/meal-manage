<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <el-input v-model="query.customerCode" clearable size="small" placeholder="客户编号" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.customerName" clearable size="small" placeholder="客户姓名" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.phone" clearable size="small" placeholder="手机号" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <rrOperation />
      </div>
      <crudOperation :permission="permission" />
    </div>

    <!--表格渲染-->
    <el-table
      ref="table"
      v-loading="crud.loading"
      :data="crud.data"
      border
      @selection-change="crud.selectionChangeHandler"
    >
      <el-table-column :selectable="checkboxT" type="selection" width="55" />
      <el-table-column label="客户编号" prop="customerCode" width="100" fixed="left" />
      <el-table-column label="姓名" prop="customerName" width="100" fixed="left" />
      <el-table-column label="手机号" prop="phone" width="120" />
      <el-table-column label="地址" prop="defaultAddress" min-width="150" />
      <el-table-column label="早餐数" prop="breakfastCount" width="80" align="center" />
      <el-table-column label="午晚数" prop="lunchDinnerCount" width="80" align="center" />
      <el-table-column label="剩余早餐" prop="remainingBreakfastCount" width="90" align="center" />
      <el-table-column label="剩余午晚" prop="remainingLunchDinnerCount" width="90" align="center" />
      <el-table-column label="送餐模式" prop="scheduleMode" width="90" align="center">
        <template slot-scope="scope">
          {{ scope.row.scheduleMode || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="过敏食物" prop="allergyTags" width="120" show-overflow-tooltip>
        <template slot-scope="scope">
          <span v-if="!scope.row.allergyTags || scope.row.allergyTags.length === 0">-</span>
          <template v-else>
            <el-tag
              v-for="(tag, i) in scope.row.allergyTags.slice(0, 3)"
              :key="i"
              size="mini"
              type="warning"
              style="margin-right: 2px;"
            >
              {{ tag }}
            </el-tag>
            <span v-if="scope.row.allergyTags.length > 3" style="font-size: 11px; color: #999;">
              +{{ scope.row.allergyTags.length - 3 }}
            </span>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="特殊要求" prop="specialRequirements" min-width="140" show-overflow-tooltip>
        <template slot-scope="scope">
          {{ scope.row.specialRequirements || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="排除菜品" prop="excludedDishNamesStr" width="160" align="center">
        <template slot-scope="scope">
          <span v-if="!scope.row.excludedDishNamesStr">无</span>
          <el-tooltip v-else :content="scope.row.excludedDishNamesStr" placement="top" :open-delay="300">
            <span class="cell-overflow">{{ scope.row.excludedDishNamesStr }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="排除日期" prop="excludedDates" width="160" align="center">
        <template slot-scope="scope">
          <span v-if="!scope.row.excludedDates || scope.row.excludedDates.length === 0">无</span>
          <el-tooltip v-else :content="formatExcludedDatesStr(scope.row.excludedDates)" placement="top" :open-delay="300">
            <span class="cell-overflow">{{ formatExcludedDatesStr(scope.row.excludedDates) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="150" />
      <el-table-column v-if="checkPer(['admin','customerProfile:edit'])" label="操作" width="180px" align="center" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" icon="edit" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button
            size="mini"
            type="success"
            icon="view"
            @click="handleDetail(scope.row)"
          >
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!--分页-->
    <el-pagination
      :current-page="crud.page.current"
      :page-sizes="[10, 20, 50, 100]"
      :page-size="crud.page.size"
      :total="crud.page.total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="crud.sizeChangeHandler"
      @current-change="crud.pageChangeHandler"
    />

    <!--表单组件-->
    <el-dialog
      append-to-body
      :close-on-click-modal="false"
      :visible.sync="dialogVisible"
      :title="dialogTitle"
      width="800px"
      top="5vh"
    >
      <div v-if="isCreateMode()" class="dialog-top-actions">
        <el-button
          type="primary"
          icon="el-icon-document-copy"
          size="small"
          @click="openIntakeDialog"
        >
          文本解析建档
        </el-button>
      </div>
      <el-form ref="form" :model="form" :rules="rules" size="small" label-width="100px">
        <!-- 客户基本信息 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="客户编号">
              <el-input v-model="form.customerCode" placeholder="不填则自动生成" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户姓名" prop="customerName">
              <el-input v-model="form.customerName" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="孕周">
              <el-input-number v-model="form.gestationalWeek" :min="1" :max="50" controls-position="right" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="过敏食物">
              <el-select
                ref="allergySelect"
                v-model="form.allergyTags"
                multiple
                filterable
                remote
                allow-create
                default-first-option
                :remote-method="searchAllergy"
                :loading="allergyLoading"
                placeholder="输入配料名称，支持逗号/顿号批量输入"
                style="width: 100%;"
                @keydown.native.capture="handleAllergyKeydown"
                @paste.native="handleAllergyPaste"
              >
                <el-option v-for="item in allergyOptions" :key="item.id" :label="item.name" :value="item.name" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="排除菜品">
              <el-select
                v-model="form.excludedDishIds"
                multiple
                filterable
                remote
                :remote-method="searchExcludedDish"
                :loading="excludedDishLoading"
                placeholder="输入菜品名称搜索并选择排除"
                style="width: 100%;"
              >
                <el-option
                  v-for="item in excludedDishOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="排除日期">
              <div>
                <el-button
                  size="mini"
                  type="text"
                  :icon="excludeDatesExpanded ? 'el-icon-arrow-up' : 'el-icon-arrow-down'"
                  @click="excludeDatesExpanded = !excludeDatesExpanded"
                >
                  {{ excludeDatesExpanded ? '收起日历' : (form.excludedDates && form.excludedDates.length > 0 ? `已选 ${form.excludedDates.length} 个日期，点击展开` : '点击展开选择') }}
                </el-button>
                <MealScheduleCalendar
                  v-show="excludeDatesExpanded"
                  v-model="form.excludedDates"
                  :readonly="false"
                  :hide-summary="true"
                  style="margin-top: 8px;"
                />
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="form.excludedDates && form.excludedDates.length > 0" :gutter="20">
          <el-col :span="24">
            <el-form-item label="已选排除日期">
              <el-table :data="form.excludedDates" size="small" border style="width: 100%">
                <el-table-column label="日期" prop="date" width="140" />
                <el-table-column label="排除餐次">
                  <template slot-scope="scope">
                    {{ formatMealTypes(scope.row.mealTypes) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                  <template slot-scope="scope">
                    <el-button size="mini" type="danger" @click="removeExcludedDate(scope.$index)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="医嘱要求">
              <el-input v-model="form.medicalRequirements" type="textarea" :rows="2" placeholder="请输入医嘱要求" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="特殊要求">
              <el-input v-model="form.specialRequirements" type="textarea" :rows="2" placeholder="请输入特殊要求" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生产日期">
              <el-date-picker
                v-model="form.productionDate"
                type="date"
                placeholder="选择生产日期"
                value-format="yyyy-MM-dd"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">地址信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="默认地址">
              <el-input v-model="form.addresses[0].addressDetail" placeholder="默认地址" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="工作日地址">
              <el-input v-model="form.addresses[1].addressDetail" placeholder="工作日地址(可选)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="周末地址">
              <el-input v-model="form.addresses[2].addressDetail" placeholder="周末地址(可选)" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 首单信息（仅新增时显示） -->
        <template v-if="isCreateMode()">
          <el-divider content-position="left">首单信息</el-divider>
          <OrderForm
            ref="orderFormRef"
            v-model="form.orderInfo"
            mode="firstOrder"
            :readonly="false"
          />
        </template>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="text" @click="cancelDialog">取消</el-button>
        <el-button :loading="submitLoading" type="primary" @click="submitForm">确认</el-button>
      </div>
    </el-dialog>

    <!-- 客户详情弹窗 -->
    <CustomerDetailDialog
      :visible.sync="detailDialogVisible"
      :customer="currentCustomer"
      @edit-customer="handleEdit"
      @view-all-orders="handleViewAllOrders"
    />

    <el-dialog
      title="客户话术解析"
      :visible.sync="intakeDialogVisible"
      width="760px"
      append-to-body
    >
      <div class="intake-guide">
        <div class="intake-guide__section">
          <div class="intake-guide__title">必填字段</div>
          <div class="intake-guide__text">
            联系人、电话、地址、套餐、合计/订餐描述、菜品配置
          </div>
        </div>
        <div class="intake-guide__section">
          <div class="intake-guide__title">解析规则</div>
          <div class="intake-guide__text intake-guide__list">
            <div>套餐只识别系统父套餐名称或编码。</div>
            <div>餐别只作参考，不当成套餐。</div>
            <div>排餐模式优先识别“配送日期”或“排餐模式”字段；“等通知”“默认等通知”“默认等通知配送”都会按指定日期送处理；订餐描述里出现“每天”“工作日”“周末”时也会自动推导；如果两边同时有值，以“配送日期/排餐模式”为准。</div>
            <div>销售渠道优先识别“来源”“客户来源”“销售渠道”字段，支持抖音、小红书、客户介绍、门店咨询，未识别时自动设为“其他”。</div>
            <div>“米换成糙米”会映射为“三色糙米”。</div>
            <div>订餐描述支持“14天每天午餐和晚餐”这类写法，自动推导餐次、开始餐次和餐数。</div>
            <div>如显式填写“开始餐次：晚餐”，则以显式值为准。</div>
            <div>生产日期支持“2026-05-02”“2026/05/02”“5月2日”这类写法。</div>
          </div>
        </div>
        <div class="intake-guide__section">
          <div class="intake-guide__title">默认值</div>
          <div class="intake-guide__text">
            未写开始日期默认当天；“默认等通知配送”按指定日期送处理；未写米饭类型默认白米饭。
          </div>
        </div>
      </div>
      <el-input
        v-model="intakeText"
        type="textarea"
        :rows="14"
        placeholder="粘贴客户信息文本"
      />
      <div v-if="intakeResult && intakeResult.issues && intakeResult.issues.length" class="intake-issues">
        <el-alert
          v-for="(issue, index) in intakeResult.issues"
          :key="index"
          :title="issue.message"
          :description="issue.sourceValue ? ('原文：' + issue.sourceValue) : ''"
          :type="issue.level === 'ERROR' ? 'error' : 'warning'"
          :closable="false"
          show-icon
          class="intake-alert"
        />
      </div>
      <div slot="footer">
        <el-button @click="intakeDialogVisible = false">取消</el-button>
        <el-button :loading="intakeParsing" type="primary" @click="handleParseIntakeText">解析</el-button>
        <el-button
          :disabled="!intakeResult || !intakeResult.draft"
          type="success"
          @click="applyIntakeDraft"
        >
          应用草稿
        </el-button>
      </div>
    </el-dialog>

  </div>
</template>

<script>
import * as profileApi from '@/api/customer/profile'
import { queryIngredients } from '@/api/dishIngredient'
import { queryDishes } from '@/api/dish'
import { MealTypeName, normalizeDeliveryDates } from '@/utils/calendar'
import MealScheduleCalendar from '@/components/Calendar/MealScheduleCalendar.vue'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'
import OrderForm, { createFirstOrderDefaultForm } from '@/components/Order/OrderForm.vue'
import CustomerDetailDialog from './CustomerDetailDialog.vue'

function createDefaultAddresses() {
  return [
    { addressType: 'DEFAULT', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WORKDAY', addressDetail: '', contactName: '', contactPhone: '' },
    { addressType: 'WEEKEND', addressDetail: '', contactName: '', contactPhone: '' }
  ]
}

const defaultForm = {
  id: null,
  customerCode: null,
  customerName: null,
  phone: null,
  gestationalWeek: null,
  allergyTags: [],
  excludedDishIds: [],
  excludedDates: [],
  medicalRequirements: null,
  specialRequirements: null,
  productionDate: null,
  remark: null,
  addresses: createDefaultAddresses(),
  orderInfo: createFirstOrderDefaultForm()
}

function cleanReplaceRules(rules) {
  if (!rules || !rules.length) return []
  return rules.filter(r => r.sourceDishId && r.targetDishId).map(r => ({
    sourceDishId: r.sourceDishId,
    sourceDishName: r.sourceDishName,
    sourceDishType: r.sourceDishType,
    targetDishId: r.targetDishId,
    targetDishName: r.targetDishName,
    targetDishType: r.targetDishType,
    remark: r.remark
  }))
}

export default {
  name: 'CustomerProfile',
  components: { crudOperation, rrOperation, OrderForm, CustomerDetailDialog, MealScheduleCalendar },
  mixins: [presenter(), header(), form(defaultForm), crud()],
  cruds() {
    return CRUD({
      title: '客户档案',
      url: '/api/customerProfile',
      idField: 'id',
      sort: 'id,desc',
      crudMethod: { ...profileApi },
      query: {
        customerCode: '',
        customerName: '',
        phone: ''
      }
    })
  },
  data() {
    return {
      permission: {
        add: ['admin', 'customerProfile:add'],
        edit: ['admin', 'customerProfile:edit'],
        del: ['admin', 'customerProfile:del']
      },
      rules: {
        customerName: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }],
        phone: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
        ],
        'orderInfo.startDate': [{ required: true, message: '请选择开始日期', trigger: 'change' }],
        'orderInfo.trialOrderId': [{
          validator: (rule, value, callback) => {
            if (this.form.orderInfo && this.form.orderInfo.trialConverted && !value) {
              callback(new Error('请选择关联试餐订单'))
              return
            }
            callback()
          },
          trigger: 'change'
        }]
      },
      submitLoading: false,
      excludeDatesExpanded: false,
      allergyOptions: [],
      allergyLoading: false,
      excludedDishOptions: [],
      excludedDishLoading: false,
      editRequestId: 0,
      detailDialogVisible: false,
      currentCustomer: null,
      intakeDialogVisible: false,
      intakeText: '',
      intakeParsing: false,
      intakeResult: null
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.crud.status.cu > 0
      },
      set(val) {
        if (!val) {
          this.crud.cancelCU()
        }
      }
    },
    dialogTitle() {
      return this.isCreateMode() ? '新增客户' : '编辑客户'
    }
  },
  methods: {
    serializeDeliveryDates(value) {
      if (typeof value === 'string') {
        const trimmed = value.trim()
        return trimmed || null
      }
      const normalized = normalizeDeliveryDates(value)
      return normalized.length > 0 ? JSON.stringify(normalized) : null
    },
    formatMealTypes(mealTypes) {
      if (!Array.isArray(mealTypes)) return ''
      return mealTypes.map(mt => MealTypeName[mt] || mt).join('，')
    },
    formatExcludedDatesStr(excludedDates) {
      if (!Array.isArray(excludedDates) || excludedDates.length === 0) return ''
      return excludedDates.map(item => {
        const mealStr = this.formatMealTypes(item.mealTypes)
        return mealStr ? `${item.date}(${mealStr})` : item.date
      }).join(', ')
    },
    removeExcludedDate(index) {
      this.form.excludedDates.splice(index, 1)
    },
    isCreateMode() {
      return this.crud.status.add === CRUD.STATUS.PREPARED
    },
    createAddressesFromForm(formData = {}) {
      const defaults = createDefaultAddresses()
      if (Array.isArray(formData.addresses)) {
        const addressMap = formData.addresses.reduce((result, address) => {
          if (address && address.addressType) {
            result[address.addressType] = {
              addressType: address.addressType,
              addressDetail: address.addressDetail || '',
              contactName: address.contactName || '',
              contactPhone: address.contactPhone || ''
            }
          }
          return result
        }, {})
        return defaults.map(address => addressMap[address.addressType] || address)
      }
      return defaults
    },
    buildSubmitPayload() {
      const formData = this.form
      const payload = {
        customerCode: formData.customerCode || null,
        customerName: formData.customerName,
        phone: formData.phone,
        gestationalWeek: formData.gestationalWeek,
        allergyTags: Array.isArray(formData.allergyTags) ? formData.allergyTags : [],
        excludedDishIds: Array.isArray(formData.excludedDishIds) ? formData.excludedDishIds : [],
        excludedDates: Array.isArray(formData.excludedDates) ? formData.excludedDates : [],
        medicalRequirements: formData.medicalRequirements,
        specialRequirements: formData.specialRequirements,
        productionDate: formData.productionDate,
        remark: formData.remark,
        addresses: this.createAddressesFromForm(formData)
      }

      if (formData.id) {
        payload.id = formData.id
      }

      if (this.isCreateMode()) {
        const orderInfo = formData.orderInfo || createFirstOrderDefaultForm()
        const breakfastCount = orderInfo.breakfastCount || 0
        const lunchDinnerCount = orderInfo.lunchDinnerCount || 0
        const deliveryDatesSource = Array.isArray(orderInfo.deliveryDatesWithMealTypes) && orderInfo.deliveryDatesWithMealTypes.length > 0
          ? orderInfo.deliveryDatesWithMealTypes
          : orderInfo.deliveryDates
        payload.orderInfo = {
          parentPackageId: orderInfo.parentPackageId,
          breakfastCount,
          lunchDinnerCount,
          totalCount: breakfastCount + lunchDinnerCount,
          breakfastPrice: orderInfo.breakfastPrice || 0,
          lunchDinnerPrice: orderInfo.lunchDinnerPrice || 0,
          totalAmount: orderInfo.totalAmount || 0,
          depositAmount: orderInfo.depositAmount || 0,
          finalAmount: orderInfo.finalAmount || 0,
          scheduleMode: orderInfo.scheduleMode || 'SCHEDULE',
          startDate: orderInfo.startDate,
          startMealType: orderInfo.startMealType || 'BREAKFAST',
          mealType: orderInfo.mealType || 'ALL',
          customerSource: orderInfo.customerSource || null,
          trialConverted: !!orderInfo.trialConverted,
          trialOrderId: orderInfo.trialConverted ? orderInfo.trialOrderId : null,
          deliveryDates: this.serializeDeliveryDates(deliveryDatesSource),
          mainDishCount: orderInfo.mainDishCount || 0,
          sideDishCount: orderInfo.sideDishCount || 0,
          vegCount: orderInfo.vegCount || 0,
          riceCount: orderInfo.riceCount == null ? 1 : orderInfo.riceCount,
          riceType: orderInfo.riceType || '白米饭',
          soupCount: orderInfo.soupCount || 0,
          replaceRules: cleanReplaceRules(orderInfo.replaceRules)
        }
      }

      return payload
    },
    cancelDialog() {
      this.crud.cancelCU()
    },
    openIntakeDialog() {
      this.intakeDialogVisible = true
      this.intakeResult = null
    },
    async handleParseIntakeText() {
      if (!this.intakeText || !this.intakeText.trim()) {
        this.$message.warning('请先粘贴客户信息文本')
        return
      }
      try {
        this.intakeParsing = true
        const res = await profileApi.parseIntakeText({ text: this.intakeText })
        this.intakeResult = res.data || res
      } catch (e) {
        console.error('parseIntakeText error', e)
        this.$message.error('解析失败：' + (e.message || '未知错误'))
      } finally {
        this.intakeParsing = false
      }
    },
    applyIntakeDraft() {
      if (!this.intakeResult || !this.intakeResult.draft) {
        return
      }
      const draft = this.intakeResult.draft
      const mergedForm = JSON.parse(JSON.stringify(defaultForm))

      Object.keys(draft).forEach(key => {
        this.$set(mergedForm, key, draft[key])
      })

      if (!mergedForm.addresses || !mergedForm.addresses.length) {
        mergedForm.addresses = createDefaultAddresses()
      } else {
        mergedForm.addresses = this.createAddressesFromForm(mergedForm)
      }

      if (!mergedForm.orderInfo) {
        mergedForm.orderInfo = createFirstOrderDefaultForm()
      } else {
        mergedForm.orderInfo = Object.assign(createFirstOrderDefaultForm(), mergedForm.orderInfo)
      }

      Object.assign(this.form, mergedForm)
      this.excludeDatesExpanded = false
      this.intakeDialogVisible = false
      this.$message.success('已应用解析草稿，请确认后保存')
    },
    async submitForm() {
      // 基础字段校验
      const basicValid = await new Promise(resolve => {
        this.$refs.form.validate((valid) => resolve(valid))
      })
      if (!basicValid) return

      // 首单信息校验（仅新增时）
      if (this.isCreateMode() && this.$refs.orderFormRef) {
        const orderValid = await this.$refs.orderFormRef.validate().catch(() => false)
        if (!orderValid) return
        if (this.form.orderInfo && this.form.orderInfo.trialConverted && !this.form.orderInfo.trialOrderId) {
          this.$message.warning('请选择关联试餐订单')
          return
        }
      }

      try {
        this.submitLoading = true
        const payload = this.buildSubmitPayload()
        if (payload.id) {
          await profileApi.edit(payload)
          this.$message.success('编辑成功')
        } else {
          await profileApi.add(payload)
          this.$message.success('新增成功')
        }
        this.crud.cancelCU()
        this.crud.refresh()
      } catch (e) {
        console.error('submit error', e)
        this.$message.error((e.message || '') || '操作失败')
      } finally {
        this.submitLoading = false
      }
    },
    [CRUD.HOOK.beforeToAdd]() {
      Object.assign(this.form, JSON.parse(JSON.stringify(defaultForm)))
      this.intakeText = ''
      this.intakeResult = null
      this.intakeDialogVisible = false
      return true
    },
    [CRUD.HOOK.beforeToCU]() {
      // 确保过敏食物是数组
      if (!this.form.allergyTags || !Array.isArray(this.form.allergyTags)) {
        this.$set(this.form, 'allergyTags', [])
      }
      // 确保排除菜品是数组
      if (!this.form.excludedDishIds || !Array.isArray(this.form.excludedDishIds)) {
        this.$set(this.form, 'excludedDishIds', [])
      }
      // 回填排除菜品的选项（编辑时详情已有 ids，需要把对应菜品预加载到 options 中）
      if (this.form.excludedDishIds.length > 0) {
        this.preloadExcludedDishes(this.form.excludedDishIds)
      }
      // 确保排除日期是数组
      if (!this.form.excludedDates || !Array.isArray(this.form.excludedDates)) {
        this.$set(this.form, 'excludedDates', [])
      }
      // 确保地址完整
      const existingTypes = (this.form.addresses || []).map(a => a.addressType)
      const requiredTypes = ['DEFAULT', 'WORKDAY', 'WEEKEND']
      const missingTypes = requiredTypes.filter(t => !existingTypes.includes(t))
      if (missingTypes.length > 0 || !this.form.addresses || this.form.addresses.length === 0) {
        const addressMap = new Map()
        ;(this.form.addresses || []).forEach(a => {
          addressMap.set(a.addressType, a)
        })
        const newAddresses = requiredTypes.map(type => {
          return addressMap.get(type) || { addressType: type, addressDetail: '', contactName: '', contactPhone: '' }
        })
        this.$set(this.form, 'addresses', newAddresses)
      }
      // 确保首单信息存在
      if (!this.form.orderInfo) {
        this.$set(this.form, 'orderInfo', createFirstOrderDefaultForm())
      }
      return true
    },
    async handleEdit(row) {
      const requestId = this.editRequestId + 1
      this.editRequestId = requestId
      try {
        const res = await profileApi.getProfile(row.id)
        if (requestId !== this.editRequestId) {
          return
        }
        const detail = res.data || res
        this.crud.toEdit(detail)
      } catch (e) {
        if (requestId !== this.editRequestId) {
          return
        }
        this.$message.error('获取客户详情失败: ' + (e.message || '未知错误'))
      }
    },
    checkboxT() {
      return true
    },
    handleDetail(row) {
      this.currentCustomer = row
      this.detailDialogVisible = true
    },
    handleViewAllOrders(customer) {
      // 跳转到订单页面并筛选该客户的订单
      this.$router.push({
        path: '/customer/order',
        query: {
          customerId: customer.id,
          customerName: customer.customerName
        }
      })
    },
    handleAllergyKeydown(e) {
      if (e.key !== 'Enter') return
      const input = e.target
      if (!input || !input.value) return
      const raw = input.value.trim()
      if (!raw) return
      const parts = raw.split(/[,，、]/).map(s => s.trim()).filter(Boolean)
      if (parts.length <= 1) return
      e.preventDefault()
      e.stopPropagation()
      this.addAllergyTags(parts, input)
    },
    handleAllergyPaste(e) {
      const text = (e.clipboardData || window.clipboardData).getData('text')
      if (!text) return
      const parts = text.split(/[,，、]/).map(s => s.trim()).filter(Boolean)
      if (parts.length <= 1) return
      e.preventDefault()
      const input = e.target
      this.addAllergyTags(parts, input)
    },
    addAllergyTags(tags, input) {
      const existing = Array.isArray(this.form.allergyTags) ? this.form.allergyTags : []
      const merged = [...existing]
      tags.forEach(tag => {
        if (!merged.includes(tag)) {
          merged.push(tag)
        }
      })
      this.$set(this.form, 'allergyTags', merged)
      this.$nextTick(() => {
        if (input) {
          input.value = ''
          const selectRef = this.$refs.allergySelect
          if (selectRef && selectRef.$refs.input) {
            selectRef.$refs.input.value = ''
          }
        }
      })
    },
    async searchAllergy(query) {
      if (!query) {
        this.allergyOptions = []
        return
      }
      this.allergyLoading = true
      try {
        const res = await queryIngredients({ name: query, page: 0, size: 20 })
        this.allergyOptions = res.content || []
      } catch (e) {
        console.error('searchAllergy error', e)
      } finally {
        this.allergyLoading = false
      }
    },
    async searchExcludedDish(query) {
      if (!query) {
        this.excludedDishOptions = []
        return
      }
      this.excludedDishLoading = true
      try {
        const res = await queryDishes({ name: query, page: 0, size: 20 })
        this.excludedDishOptions = res.content || []
      } catch (e) {
        console.error('searchExcludedDish error', e)
      } finally {
        this.excludedDishLoading = false
      }
    },
    async preloadExcludedDishes(ids) {
      // 编辑时将已选的 excludedDishIds 对应菜品信息加载到选项中，保证 label 正常显示
      try {
        const res = await queryDishes({ ids: ids.join(','), page: 0, size: ids.length + 10 })
        const dishes = res.content || []
        // 合并已有选项，避免覆盖用户当前搜索结果
        const existingIds = new Set(this.excludedDishOptions.map(d => d.id))
        dishes.forEach(d => {
          if (!existingIds.has(d.id)) {
            this.excludedDishOptions.push(d)
          }
        })
      } catch (e) {
        console.error('preloadExcludedDishes error', e)
      }
    }
  }
}
</script>

<style scoped>
.cell-overflow {
  display: inline-block;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  cursor: default;
}
.head-container {
  padding: 10px;
  margin-bottom: 10px;
}
.head-container .filter-item {
  margin-right: 10px;
}
.dialog-top-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.intake-issues {
  margin-top: 12px;
}
.intake-alert {
  margin-bottom: 8px;
}
.intake-guide {
  margin-bottom: 12px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e6edf5;
  border-radius: 4px;
}
.intake-guide__section + .intake-guide__section {
  margin-top: 8px;
}
.intake-guide__title {
  margin-bottom: 2px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.intake-guide__text {
  font-size: 12px;
  line-height: 1.6;
  color: #606266;
}
.intake-guide__list > div + div {
  margin-top: 2px;
}
</style>
