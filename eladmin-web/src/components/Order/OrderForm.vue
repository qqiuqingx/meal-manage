<template>
  <div class="order-form">
    <el-form ref="elForm" :model="form" :rules="rules" size="small" label-width="110px">

      <!-- ===== 订单管理模式专属：客户 + 订单状态 ===== -->
      <template v-if="mode === 'order'">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select
                v-model="form.customerId"
                filterable
                remote
                :disabled="customerDisabled"
                placeholder="输入客户姓名或手机号搜索"
                :remote-method="searchCustomer"
                :loading="customerLoading"
                style="width: 100%;"
                @change="onCustomerChange"
              >
                <el-option v-for="item in customers" :key="item.id" :label="formatCustomerLabel(item)" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单状态" prop="status">
              <el-select v-model="form.status" :disabled="readonly" placeholder="请选择状态" style="width: 100%;">
                <el-option label="进行中" :value="1" />
                <el-option label="已完成" :value="2" />
                <el-option label="已取消" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- ===== 公共字段：销售渠道 + 排餐模式（两种模式均显示） ===== -->
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="销售渠道">
            <el-select v-model="form.customerSource" :disabled="readonly" clearable placeholder="选择销售渠道" style="width: 100%;">
              <el-option v-for="item in customerSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="排餐模式">
            <el-select v-model="form.scheduleMode" :disabled="readonly" placeholder="请选择排餐模式" style="width: 100%;">
              <el-option label="指定日期送" value="SCHEDULE" />
              <el-option label="每天送" value="DAILY" />
              <el-option label="周末送" value="WEEKEND" />
              <el-option label="工作日送" value="WEEKDAY" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="餐次类型">
            <el-select v-model="form.mealType" :disabled="readonly" placeholder="请选择餐次类型" style="width: 100%;">
              <el-option label="早+午餐+晚餐（默认）" value="ALL" />
              <el-option label="午餐+晚餐" value="LUNCH_DINNER" />
              <el-option label="午餐订单" value="LUNCH" />
              <el-option label="晚餐订单" value="DINNER" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="试餐成单" prop="trialConverted">
            <el-radio-group v-model="form.trialConverted" :disabled="readonly" @change="onTrialConvertedChange">
              <el-radio :label="false">否</el-radio>
              <el-radio :label="true">是</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col v-if="form.trialConverted" :span="16">
          <el-form-item label="关联试餐订单" prop="trialOrderId">
            <el-select
              v-model="form.trialOrderId"
              filterable
              remote
              clearable
              :disabled="readonly"
              placeholder="输入订单编号、客户姓名或手机号搜索"
              :remote-method="searchTrialOrders"
              :loading="trialOrderLoading"
              style="width: 100%;"
              @change="onTrialOrderChange"
            >
              <el-option
                v-for="item in trialOrderOptions"
                :key="item.id"
                :label="formatTrialOrderLabel(item)"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 指定日期送时显示送餐日期选择 -->
      <el-row v-if="form.scheduleMode === 'SCHEDULE'" :gutter="20">
        <el-col :span="24">
          <el-form-item label="送餐日期">
            <MealScheduleCalendar
              v-model="form.deliveryDatesWithMealTypes"
              :start-date="form.startDate"
              :start-meal-type="form.startMealType"
              :readonly="readonly"
              :order-meal-type="form.mealType"
              @selection-change="onCalendarSelectionChange"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- ===== 父套餐（用于编号池归属，两种模式均显示） ===== -->
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="父套餐">
            <el-select
              v-model="form.parentPackageId"
              :disabled="readonly"
              clearable
              placeholder="选择父套餐"
              style="width: 100%;"
              @change="onParentPackageChange"
            >
              <el-option v-for="item in parentPackages" :key="item.id" :label="item.packageName" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- ===== 每餐菜品配置 ===== -->
      <el-divider content-position="left">每餐菜品配置</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="主菜数(份)">
            <el-input-number
              v-model="form.mainDishCount"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="副菜数(份)">
            <el-input-number
              v-model="form.sideDishCount"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="素菜数(份)">
            <el-input-number
              v-model="form.vegCount"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="米饭类型">
            <el-select
              v-model="form.riceType"
              :disabled="readonly"
              placeholder="请选择米饭类型"
              style="width: 100%;"
            >
              <el-option
                v-for="item in riceTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="汤数(份)">
            <el-input-number
              v-model="form.soupCount"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- ===== 换菜规则 ===== -->
      <el-divider content-position="left">换菜规则</el-divider>
      <div class="replace-rules-section">
        <p class="replace-rules-tip" style="color: #909399; font-size: 12px; margin: 0 0 10px 0;">
          当排餐命中原菜时，将自动替换为目标菜，规则全订单生效
        </p>
        <div v-for="(rule, index) in form.replaceRules" :key="index" class="replace-rule-row">
          <el-row :gutter="10">
            <el-col :span="9">
              <el-select
                v-model="rule.sourceDishId"
                :disabled="readonly"
                filterable
                remote
                placeholder="搜索原菜品"
                :remote-method="query => searchDishForRule(query, 'source', index)"
                :loading="rule._sourceLoading"
                style="width: 100%;"
                @change="val => onRuleDishChange(val, 'source', index)"
              >
                <el-option
                  v-for="item in (rule._sourceOptions || [])"
                  :key="item.id"
                  :label="item.name + '（' + (item.dishType || '') + '）'"
                  :value="item.id"
                />
                <template v-if="rule.sourceDishName && (!rule._sourceOptions || rule._sourceOptions.length === 0)">
                  <el-option :label="rule.sourceDishName + (rule.sourceDishInvalid ? '（已失效）' : '')" :value="rule.sourceDishId" />
                </template>
              </el-select>
            </el-col>
            <el-col :span="1" style="text-align: center; line-height: 32px;">
              <i class="el-icon-right" />
            </el-col>
            <el-col :span="9">
              <el-select
                v-model="rule.targetDishId"
                :disabled="readonly"
                filterable
                remote
                placeholder="搜索目标菜品"
                :remote-method="query => searchDishForRule(query, 'target', index)"
                :loading="rule._targetLoading"
                style="width: 100%;"
                @change="val => onRuleDishChange(val, 'target', index)"
              >
                <el-option
                  v-for="item in (rule._targetOptions || [])"
                  :key="item.id"
                  :label="item.name + '（' + (item.dishType || '') + '）'"
                  :value="item.id"
                />
                <template v-if="rule.targetDishName && (!rule._targetOptions || rule._targetOptions.length === 0)">
                  <el-option :label="rule.targetDishName + (rule.targetDishInvalid ? '（已失效）' : '')" :value="rule.targetDishId" />
                </template>
              </el-select>
            </el-col>
            <el-col :span="3">
              <el-input v-model="rule.remark" :disabled="readonly" placeholder="备注" size="small" />
            </el-col>
            <el-col :span="2" style="text-align: center;">
              <el-button v-if="!readonly" type="danger" icon="el-icon-delete" circle size="mini" @click="removeReplaceRule(index)" />
            </el-col>
          </el-row>
        </div>
        <el-button v-if="!readonly" type="primary" plain size="small" icon="el-icon-plus" @click="addReplaceRule">
          新增规则
        </el-button>
      </div>

      <!-- ===== 客户饮食信息（仅订单模式） ===== -->
      <template v-if="mode === 'order'">
        <el-divider content-position="left">客户饮食信息</el-divider>
        <el-alert
          title="修改后会同步更新客户档案，影响该客户后续全部订单的排餐"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 15px;"
        />
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
                :disabled="readonly"
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
            <el-form-item label="特殊要求">
              <el-input
                v-model="form.specialRequirements"
                type="textarea"
                :rows="3"
                :disabled="readonly"
                placeholder="客户特殊要求"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- ===== 金额信息 ===== -->
      <el-divider content-position="left">金额信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="总金额(元)" prop="totalAmount">
            <el-input-number
              v-model="form.totalAmount"
              :min="0"
              :precision="2"
              :disabled="readonly || mode === 'firstOrder'"
              controls-position="right"
              style="width: 100%;"
              @change="calcBalance"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="成交金额(元)" prop="finalAmount">
            <el-input-number
              v-model="form.finalAmount"
              :min="0"
              :precision="2"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
              @change="calcBalance"
            />
          </el-form-item>
        </el-col>
        <!-- 定金：两种模式均显示，去除重复 -->
        <el-col :span="8">
          <el-form-item label="定金(元)">
            <el-input-number
              v-model="form.depositAmount"
              :min="0"
              :precision="2"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="早餐单价(元)">
            <el-input-number
              v-model="form.breakfastPrice"
              :min="0"
              :precision="2"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
              @change="calcTotalAmount"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="午餐晚餐单价(元)">
            <el-input-number
              v-model="form.lunchDinnerPrice"
              :min="0"
              :precision="2"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
              @change="calcTotalAmount"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- ===== 餐数信息 ===== -->
      <el-divider content-position="left">餐数信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="早餐合计(份)">
            <el-input-number
              v-model="form.breakfastCount"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
              @change="onMealCountChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="午餐+晚餐(份)">
            <el-input-number
              v-model="form.lunchDinnerCount"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              style="width: 100%;"
              @change="onMealCountChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="合计(份)">
            <el-input-number
              :value="totalCount"
              :min="0"
              disabled
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <!-- 首单模式：自动计算总价展示 -->
        <el-col v-if="mode === 'firstOrder'" :span="8">
          <el-form-item label="总价(自动)">
            <el-input-number
              :value="form.totalAmount"
              :min="0"
              disabled
              :precision="2"
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- ===== 核销信息（仅订单模式） ===== -->
      <template v-if="mode === 'order'">
        <el-divider content-position="left">核销信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="核销餐数(合计)">
              <el-input-number
                v-model="form.verifiedCount"
                :min="0"
                :disabled="readonly"
                controls-position="right"
                style="width: 100%;"
                @change="calcRemaining"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="核销金额(元)">
              <el-input-number
                v-model="form.verifiedAmount"
                :min="0"
                :precision="2"
                :disabled="readonly"
                controls-position="right"
                style="width: 100%;"
                @change="calcBalance"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="剩余餐数">
              <el-input-number
                v-model="form.remainingCount"
                :min="0"
                disabled
                controls-position="right"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="餐费余额">
              <el-input-number
                v-model="form.mealBalance"
                :min="0"
                :precision="2"
                disabled
                controls-position="right"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- ===== 日期信息（公共，但订单模式多成交时间 + 第一次送餐时间） ===== -->
      <el-divider content-position="left">日期信息</el-divider>
      <template v-if="mode === 'order'">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="成交时间">
              <el-date-picker
                v-model="form.dealTime"
                :disabled="readonly"
                type="datetime"
                placeholder="选择成交时间"
                style="width: 100%;"
                value-format="yyyy-MM-dd HH:mm:ss"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="第一次送餐时间">
              <el-date-picker
                v-model="form.firstDeliveryTime"
                :disabled="readonly"
                type="datetime"
                placeholder="选择送餐时间"
                style="width: 100%;"
                value-format="yyyy-MM-dd HH:mm:ss"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- 开始日期和餐次：两种模式均显示 -->
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item
            :label="mode === 'order' ? '订单开始日期' : '开始日期'"
            :prop="mode === 'firstOrder' ? 'startDate' : ''"
          >
            <el-date-picker
              v-model="form.startDate"
              :disabled="readonly"
              type="date"
              placeholder="选择开始日期"
              style="width: 100%;"
              value-format="yyyy-MM-dd"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="开始餐次">
            <el-select v-model="form.startMealType" :disabled="readonly" placeholder="请选择开始餐次" style="width: 100%;">
              <el-option
                v-for="item in startMealTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

    </el-form>
  </div>
</template>

<script>
import * as profileApi from '@/api/customer/profile'
import * as orderApi from '@/api/customer/order'
import * as packageApi from '@/api/customer/package'
import * as dictDetailApi from '@/api/system/dictDetail'
import * as dishApi from '@/api/dish'
import { queryIngredients } from '@/api/dishIngredient'
import MealScheduleCalendar from '@/components/Calendar/MealScheduleCalendar.vue'
import { normalizeDeliveryDates } from '@/utils/calendar'

export const DEFAULT_RICE_TYPE_OPTION_VALUE = '默认'
export const START_MEAL_TYPE_OPTIONS = {
  ALL: [
    { label: '早餐开始', value: 'BREAKFAST' },
    { label: '午餐开始', value: 'LUNCH' },
    { label: '晚餐开始', value: 'DINNER' }
  ],
  LUNCH_DINNER: [
    { label: '午餐开始', value: 'LUNCH' },
    { label: '晚餐开始', value: 'DINNER' }
  ],
  LUNCH: [
    { label: '午餐开始', value: 'LUNCH' }
  ],
  DINNER: [
    { label: '晚餐开始', value: 'DINNER' }
  ]
}

function getDefaultStartMealType(mealType) {
  if (mealType === 'DINNER') return 'DINNER'
  if (mealType === 'LUNCH' || mealType === 'LUNCH_DINNER') return 'LUNCH'
  return 'BREAKFAST'
}

function serializeDeliveryDates(value) {
  const normalized = normalizeDeliveryDates(value)
  return normalized.length > 0 ? JSON.stringify(normalized) : null
}

function normalizeNumericValue(value) {
  if (value === '' || value === null || value === undefined) {
    return null
  }
  const normalized = Number(value)
  return Number.isNaN(normalized) ? value : normalized
}

// 订单模式默认数据
export function createOrderDefaultForm() {
  return {
    id: null,
    customerId: null,
    customerCode: null,
    orderCode: null,
    parentPackageId: null,
    childPackageId: null,
    depositAmount: 0,
    totalAmount: null,
    finalAmount: null,
    breakfastCount: 0,
    lunchDinnerCount: 0,
    breakfastPrice: 0,
    lunchDinnerPrice: 0,
    verifiedCount: 0,
    verifiedAmount: 0,
    mealBalance: 0,
    remainingCount: 0,
    dealTime: null,
    firstDeliveryTime: null,
    startDate: null,
    startMealType: 'BREAKFAST',
    status: 1,
    mealType: 'ALL',
    scheduleMode: 'SCHEDULE',
    deliveryDatesWithMealTypes: [],
    deliveryDates: [],
    remark: null,
    customerSource: null,
    mainDishCount: 0,
    sideDishCount: 0,
    vegCount: 0,
    riceCount: 1,
    riceType: DEFAULT_RICE_TYPE_OPTION_VALUE,
    soupCount: 0,
    trialConverted: false,
    trialOrderId: null,
    trialOrderCode: null,
    replaceRules: [],
    allergyTags: [],
    specialRequirements: null
  }
}

// 首单模式默认数据
export function createFirstOrderDefaultForm() {
  return {
    parentPackageId: null,
    childPackageId: null,
    customerCode: null,
    breakfastCount: 0,
    lunchDinnerCount: 0,
    totalCount: 0,
    breakfastPrice: 0,
    lunchDinnerPrice: 0,
    depositAmount: 0,
    totalAmount: 0,
    finalAmount: 0,
    scheduleMode: 'SCHEDULE',
    deliveryDatesWithMealTypes: [],
    deliveryDates: [],
    startDate: null,
    startMealType: 'BREAKFAST',
    mealType: 'ALL',
    customerSource: null,
    mainDishCount: 0,
    sideDishCount: 0,
    vegCount: 0,
    riceCount: 1,
    riceType: DEFAULT_RICE_TYPE_OPTION_VALUE,
    soupCount: 0,
    trialConverted: false,
    trialOrderId: null,
    trialOrderCode: null,
    replaceRules: []
  }
}

export default {
  name: 'OrderForm',
  components: {
    MealScheduleCalendar
  },
  props: {
    // 表单数据（通过 v-model 双向绑定）
    value: {
      type: Object,
      required: true
    },
    // 模式: 'order' = 订单管理, 'firstOrder' = 客户首单
    mode: {
      type: String,
      default: 'order',
      validator: val => ['order', 'firstOrder'].includes(val)
    },
    // 是否只读
    readonly: {
      type: Boolean,
      default: false
    },
    // 是否禁用客户选择（订单模式编辑时锁定客户）
    customerDisabled: {
      type: Boolean,
      default: false
    },
    // 表单验证规则
    rules: {
      type: Object,
      default: () => ({})
    },
    // 当前客户数据（编辑时传入，用于下拉列表显示）
    currentCustomer: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      customers: [],
      customerLoading: false,
      parentPackages: [],
      childPackages: [],
      customerSourceOptions: [],
      riceTypeOptions: [],
      allergyOptions: [],
      allergyLoading: false,
      trialOrderOptions: [],
      trialOrderLoading: false,
      availableDates: [],
      hydratingForm: false,
      hydrationTimer: null
    }
  },
  computed: {
    form: {
      get() {
        return this.value
      },
      set(val) {
        this.$emit('input', val)
      }
    },
    totalCount() {
      const breakfast = this.form.breakfastCount || 0
      const lunchDinner = this.form.lunchDinnerCount || 0
      return breakfast + lunchDinner
    },
    // 日历组件使用的格式：[{date: 'yyyy-MM-dd', mealTypes: [...]}, ...]
    deliveryDatesWithMealTypes: {
      get() {
        return this.form.deliveryDatesWithMealTypes || []
      },
      set(val) {
        this.form.deliveryDatesWithMealTypes = val
      }
    },
    startMealTypeOptions() {
      return START_MEAL_TYPE_OPTIONS[this.form.mealType] || START_MEAL_TYPE_OPTIONS.ALL
    }
  },
  watch: {
    value: {
      handler(val) {
        console.log('[OrderForm] watch.value triggered, deliveryDates:', val && val.deliveryDates)
        this.beginFormHydration()
        this.normalizeFormNumbers()
        this.ensureRiceTypeValue(val)
        this.ensureRiceTypeOption(val && val.riceType)
        this.syncStartMealType()
        this.ensureTrialOrderOption(val)
        // 当外部 value 变化时（如编辑时加载数据），同步子套餐列表和日历数据
        if (val.parentPackageId) {
          this.loadChildPackages(val.parentPackageId)
        }
        this.syncCalendarSelectionFromDeliveryDates(val && val.deliveryDates)
        this.initReplaceRuleOptions(val && val.replaceRules)
      },
      immediate: true
    },
    'form.deliveryDatesWithMealTypes': {
      handler(val) {
        this.syncSerializedDeliveryDates(val)
      },
      deep: true,
      immediate: true
    },
    'form.mealType'() {
      this.syncStartMealType()
    },
    'form.trialConverted'(val) {
      if (!val) {
        this.$set(this.form, 'trialOrderId', null)
        this.$set(this.form, 'trialOrderCode', null)
      }
    },
    currentCustomer: {
      handler(val) {
        this.syncCurrentCustomerOption(val)
      },
      immediate: true,
      deep: true
    }
  },
  created() {
    this.loadCustomerSourceDict()
    this.loadRiceTypeOptions()
    this.loadParentPackages()
    this.initAvailableDates()
    this.syncCurrentCustomerOption(this.currentCustomer)
  },
  beforeDestroy() {
    if (this.hydrationTimer) {
      clearTimeout(this.hydrationTimer)
      this.hydrationTimer = null
    }
  },
  methods: {
    beginFormHydration() {
      this.hydratingForm = true
      if (this.hydrationTimer) {
        clearTimeout(this.hydrationTimer)
      }
      this.hydrationTimer = setTimeout(() => {
        this.hydratingForm = false
        this.hydrationTimer = null
      }, 0)
    },
    normalizeFormNumbers() {
      const numericFields = [
        'depositAmount',
        'totalAmount',
        'finalAmount',
        'breakfastCount',
        'lunchDinnerCount',
        'breakfastPrice',
        'lunchDinnerPrice',
        'verifiedCount',
        'verifiedAmount',
        'mealBalance',
        'remainingCount',
        'mainDishCount',
        'sideDishCount',
        'vegCount',
        'riceCount',
        'soupCount'
      ]
      numericFields.forEach(field => {
        if (Object.prototype.hasOwnProperty.call(this.form, field)) {
          const normalized = normalizeNumericValue(this.form[field])
          if (this.form[field] !== normalized) {
            this.$set(this.form, field, normalized)
          }
        }
      })
    },
    syncCalendarSelectionFromDeliveryDates(value) {
      const normalized = normalizeDeliveryDates(value)
      const current = normalizeDeliveryDates(this.form.deliveryDatesWithMealTypes)
      console.log('[OrderForm] normalized deliveryDatesWithMealTypes:', JSON.stringify(normalized))
      if (JSON.stringify(normalized) !== JSON.stringify(current)) {
        this.$set(this.form, 'deliveryDatesWithMealTypes', normalized)
      }
    },
    syncSerializedDeliveryDates(value) {
      const serialized = serializeDeliveryDates(value)
      if (this.form.deliveryDates !== serialized) {
        this.$set(this.form, 'deliveryDates', serialized)
      }
    },
    // 加载销售渠道字典
    async loadCustomerSourceDict() {
      try {
        const res = await dictDetailApi.get('customer_source')
        this.customerSourceOptions = (res.content || res.data || res || []).map(item => ({
          value: item.value,
          label: item.label
        }))
      } catch (e) {
        console.error('loadCustomerSourceDict error', e)
      }
    },
    ensureRiceTypeOption(riceType) {
      if (!riceType || riceType === DEFAULT_RICE_TYPE_OPTION_VALUE) {
        return
      }
      const exists = this.riceTypeOptions.some(item => item.value === riceType)
      if (!exists) {
        this.riceTypeOptions = this.riceTypeOptions.concat([{ label: riceType, value: riceType }])
      }
    },
    ensureRiceTypeValue(formValue) {
      if (!formValue || formValue.riceType) {
        return
      }
      this.$set(this.form, 'riceType', DEFAULT_RICE_TYPE_OPTION_VALUE)
    },
    syncStartMealType() {
      const options = this.startMealTypeOptions
      if (!options.length) {
        return
      }
      const current = this.form.startMealType
      const valid = options.some(item => item.value === current)
      if (!valid) {
        this.$set(this.form, 'startMealType', getDefaultStartMealType(this.form.mealType))
      }
    },
    ensureTrialOrderOption(formValue) {
      if (!formValue || !formValue.trialOrderId) {
        return
      }
      const exists = this.trialOrderOptions.some(item => item.id === formValue.trialOrderId)
      if (!exists) {
        this.trialOrderOptions = this.trialOrderOptions.concat([{
          id: formValue.trialOrderId,
          orderCode: formValue.trialOrderCode || ('订单ID ' + formValue.trialOrderId),
          customerName: formValue.customerName,
          phone: formValue.phone,
          parentPackageName: '试餐订单'
        }])
      }
    },
    async loadRiceTypeOptions() {
      try {
        const res = await dishApi.queryDishes({ dishType: 'RICE_TYPE', enabled: true, page: 0, size: 200 })
        const dishes = res.content || []
        this.riceTypeOptions = [{
          label: '默认',
          value: DEFAULT_RICE_TYPE_OPTION_VALUE
        }].concat(dishes.map(item => ({
          label: item.name,
          value: item.name
        })))
      } catch (e) {
        console.error('loadRiceTypeOptions error', e)
      } finally {
        this.ensureRiceTypeOption(this.form.riceType)
      }
    },
    // 日历选择变更处理
    onCalendarSelectionChange(mealCounts) {
      this.$set(this.form, 'breakfastCount', mealCounts.breakfastCount)
      this.$set(this.form, 'lunchDinnerCount', mealCounts.lunchDinnerCount)
      this.onMealCountChange()
    },
    // 初始化可选日期（未来30天）
    initAvailableDates() {
      const dates = []
      const today = new Date()
      for (let i = 1; i <= 30; i++) {
        const date = new Date(today)
        date.setDate(today.getDate() + i)
        const year = date.getFullYear()
        const month = String(date.getMonth() + 1).padStart(2, '0')
        const day = String(date.getDate()).padStart(2, '0')
        dates.push(`${year}-${month}-${day}`)
      }
      this.availableDates = dates
    },
    // 远程搜索客户
    async searchCustomer(query) {
      if (!query) return
      this.customerLoading = true
      try {
        const res = await profileApi.getProfiles({ customerName: query, size: 20 })
        this.customers = res.content || []
      } catch (e) {
        console.error('searchCustomer error', e)
      } finally {
        this.customerLoading = false
      }
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
    // 远程搜索过敏食物（配料）
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
    async searchTrialOrders(query) {
      if (!query) {
        this.trialOrderOptions = []
        this.ensureTrialOrderOption(this.form)
        return
      }
      this.trialOrderLoading = true
      try {
        const res = await orderApi.getTrialOrderOptions({ keyword: query, excludeId: this.form.id })
        this.trialOrderOptions = res.data || res || []
        this.ensureTrialOrderOption(this.form)
      } catch (e) {
        console.error('searchTrialOrders error', e)
      } finally {
        this.trialOrderLoading = false
      }
    },
    formatTrialOrderLabel(item) {
      if (!item) return ''
      const parts = [
        item.orderCode || ('订单ID ' + item.id),
        item.customerName,
        item.phone,
        item.parentPackageName
      ].filter(Boolean)
      return parts.join(' / ')
    },
    onTrialConvertedChange(val) {
      if (!val) {
        this.$set(this.form, 'trialOrderId', null)
        this.$set(this.form, 'trialOrderCode', null)
      }
    },
    onTrialOrderChange(orderId) {
      const order = this.trialOrderOptions.find(item => item.id === orderId)
      this.$set(this.form, 'trialOrderCode', order ? order.orderCode : null)
    },
    // 客户选择变更
    onCustomerChange(customerId) {
      const customer = this.customers.find(c => c.id === customerId)
      if (customer) {
        this.form.customerCode = customer.customerCode || null
        this.form.customerName = customer.customerName
        this.form.phone = customer.phone
      }
      this.$emit('customer-change', customerId, customer)
    },
    // 餐数变更
    onMealCountChange() {
      if (this.mode === 'firstOrder') {
        this.form.totalCount = this.totalCount
      }
      // 两种模式都重新计算总价
      this.calcTotalAmount()
      this.calcRemaining()
      this.$emit('calc-change')
    },
    // 计算餐费余额
    calcBalance() {
      const finalAmt = this.form.finalAmount || 0
      const verifiedAmt = this.form.verifiedAmount || 0
      this.form.mealBalance = Math.max(0, finalAmt - verifiedAmt)
      this.$emit('calc-change')
    },
    // 计算剩余餐数
    calcRemaining() {
      const total = this.totalCount
      const verified = this.form.verifiedCount || 0
      this.form.remainingCount = Math.max(0, total - verified)
      this.$emit('calc-change')
    },
    // 计算总价（两种模式均支持自动计算）
    calcTotalAmount() {
      if (this.mode === 'order' && this.hydratingForm) {
        return
      }
      // 计算基础总价
      const breakfastCount = this.form.breakfastCount || 0
      const lunchDinnerCount = this.form.lunchDinnerCount || 0
      const breakfastPrice = this.form.breakfastPrice || 0
      const lunchDinnerPrice = this.form.lunchDinnerPrice || 0
      const totalAmount = breakfastCount * breakfastPrice + lunchDinnerCount * lunchDinnerPrice

      // 首单模式：直接设置总价
      if (this.mode === 'firstOrder') {
        this.form.totalAmount = totalAmount
        // 如果成交金额未填写，默认等于总价
        if (!this.form.finalAmount && totalAmount > 0) {
          this.form.finalAmount = totalAmount
        }
      } else {
        // 订单模式：每次都重新计算总金额
        this.form.totalAmount = totalAmount
      }
      this.$emit('calc-change')
    },
    // 父套餐变更
    async onParentPackageChange(parentId) {
      this.form.childPackageId = null
      await this.loadChildPackages(parentId)
      this.$emit('package-change', parentId, null)
    },
    // 子套餐变更
    onChildPackageChange(childPackageId) {
      this.$emit('package-change', this.form.parentPackageId, childPackageId)
    },
    // 加载父套餐列表
    async loadParentPackages() {
      try {
        const res = await packageApi.getParents()
        const list = res.content || res.data || res || []
        this.parentPackages = Array.isArray(list) ? list : []
      } catch (e) {
        console.error('loadParentPackages error', e)
      }
    },
    // 加载子套餐列表
    async loadChildPackages(parentId) {
      if (!parentId) {
        this.childPackages = []
        return
      }
      try {
        const res = await packageApi.getTree()
        const tree = res.data || res || []
        const parent = tree.find(p => p.id === parentId)
        this.childPackages = parent ? (parent.children || []) : []
      } catch (e) {
        console.error('loadChildPackages error', e)
      }
    },
    // 编辑时：如果当前客户不在列表中，追加进去
    ensureCustomerInList(customerId, customerName, phone) {
      if (!customerId || !customerName) return
      const exists = this.customers.find(c => c.id === customerId)
      if (!exists) {
        this.customers.push({ id: customerId, customerName, phone })
      }
    },
    // 重置表单
    reset() {
      const factory = this.mode === 'firstOrder' ? createFirstOrderDefaultForm : createOrderDefaultForm
      const defaultData = factory()
      Object.keys(defaultData).forEach(key => {
        this.$set(this.form, key, defaultData[key])
      })
      this.childPackages = []
    },
    // 表单校验
    validate() {
      return new Promise((resolve, reject) => {
        this.$refs.elForm && this.$refs.elForm.validate((valid) => {
          if (valid) resolve(true)
          else reject(new Error('validation failed'))
        })
      })
    },

    // ========== 换菜规则 ==========

    addReplaceRule() {
      if (!this.form.replaceRules) {
        this.$set(this.form, 'replaceRules', [])
      }
      this.form.replaceRules.push({
        sourceDishId: null,
        sourceDishName: null,
        sourceDishType: null,
        targetDishId: null,
        targetDishName: null,
        targetDishType: null,
        remark: null,
        _sourceOptions: [],
        _targetOptions: [],
        _sourceLoading: false,
        _targetLoading: false
      })
    },

    removeReplaceRule(index) {
      this.form.replaceRules.splice(index, 1)
    },

    searchDishForRule(query, field, index) {
      if (!query || query.length < 1) return
      const rule = this.form.replaceRules[index]
      if (!rule) return
      this.$set(rule, '_' + field + 'Loading', true)
      dishApi.queryDishes({ name: query, enabled: true, page: 0, size: 20 }).then(res => {
        const dishes = res.content || res.data || []
        this.$set(rule, '_' + field + 'Options', dishes)
      }).catch(() => {
        this.$set(rule, '_' + field + 'Options', [])
      }).finally(() => {
        this.$set(rule, '_' + field + 'Loading', false)
      })
    },

    onRuleDishChange(val, field, index) {
      const rule = this.form.replaceRules[index]
      if (!rule) return
      const options = rule['_' + field + 'Options'] || []
      const dish = options.find(d => d.id === val)
      if (dish) {
        this.$set(rule, field + 'DishName', dish.name)
        this.$set(rule, field + 'DishType', dish.dishType)
      }
    },

    /**
     * 格式化客户下拉展示文本，优先显示客户编号，避免编辑时只回显主键 ID。
     */
    formatCustomerLabel(customer) {
      if (!customer) return ''
      return customer.customerCode || String(customer.id || '')
    },

    /**
     * 将当前编辑客户同步进下拉选项，确保禁用态也能正确回显客户编号。
     */
    syncCurrentCustomerOption(currentCustomer) {
      if (this.mode !== 'order' || !currentCustomer || !currentCustomer.id) {
        return
      }
      const option = {
        id: currentCustomer.id,
        customerCode: currentCustomer.customerCode || currentCustomer.customerName || currentCustomer.name || String(currentCustomer.id),
        customerName: currentCustomer.customerName || currentCustomer.name,
        phone: currentCustomer.phone
      }
      const exists = this.customers.find(item => item.id === option.id)
      if (exists) {
        Object.assign(exists, option)
      } else {
        this.customers = [option].concat(this.customers)
      }
      if (this.form.customerCode !== option.customerCode) {
        this.$set(this.form, 'customerCode', option.customerCode)
      }
    },

    initReplaceRuleOptions(rules) {
      if (!rules || !rules.length) return
      rules.forEach(rule => {
        // 为回显初始化 options 列表，确保 select 组件能显示已选中的值
        if (rule.sourceDishId && rule.sourceDishName) {
          this.$set(rule, '_sourceOptions', [{
            id: rule.sourceDishId,
            name: rule.sourceDishName,
            dishType: rule.sourceDishType
          }])
        }
        if (rule.targetDishId && rule.targetDishName) {
          this.$set(rule, '_targetOptions', [{
            id: rule.targetDishId,
            name: rule.targetDishName,
            dishType: rule.targetDishType
          }])
        }
        if (!rule._sourceLoading) this.$set(rule, '_sourceLoading', false)
        if (!rule._targetLoading) this.$set(rule, '_targetLoading', false)
      })
    }
  }
}
</script>

<style scoped>
.order-form {
  width: 100%;
}
.replace-rules-section {
  margin-bottom: 10px;
}
.replace-rule-row {
  margin-bottom: 8px;
}
.replace-rules-tip {
  color: #909399;
  font-size: 12px;
}
</style>
