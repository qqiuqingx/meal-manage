<template>
  <div class="order-form">
    <el-form ref="elForm" :model="form" :rules="rules" size="small" label-width="110px">
      <!-- 订单管理模式：客户选择 + 订单状态 -->
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
                <el-option v-for="item in customers" :key="item.id" :label="item.customerName + ' - ' + item.phone" :value="item.id" />
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
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排餐模式">
              <el-select v-model="form.scheduleMode" :disabled="readonly" placeholder="请选择排餐模式" style="width: 100%;">
                <el-option label="指定日期送" value="SCHEDULE" />
                <el-option label="每天送" value="DAILY" />
                <el-option label="周末送" value="WEEKEND" />
                <el-option label="工作日送" value="WEEKDAY" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="form.scheduleMode === 'SCHEDULE'" :gutter="20">
          <el-col :span="24">
            <el-form-item label="送餐日期">
              <el-select
                v-model="form.deliveryDates"
                multiple
                filterable
                allow-create
                placeholder="选择或输入送餐日期（格式：yyyy-MM-dd）"
                style="width: 100%;"
              >
                <el-option
                  v-for="date in availableDates"
                  :key="date"
                  :label="date"
                  :value="date"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">金额信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="销售渠道">
              <el-select v-model="form.customerSource" :disabled="readonly" clearable placeholder="选择销售渠道" style="width: 100%;">
                <el-option v-for="item in customerSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- 客户首单模式：套餐选择 -->
      <template v-if="mode === 'firstOrder'">
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
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="父套餐" prop="parentPackageId">
              <el-select v-model="form.parentPackageId" :disabled="readonly" placeholder="选择父套餐" style="width: 100%;" @change="onParentPackageChange">
                <el-option v-for="item in parentPackages" :key="item.id" :label="item.categoryName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="子套餐" prop="childPackageId">
              <el-select v-model="form.childPackageId" :disabled="readonly" placeholder="选择子套餐" style="width: 100%;" @change="onChildPackageChange">
                <el-option v-for="item in childPackages" :key="item.id" :label="item.categoryName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">餐数信息</el-divider>
      </template>

      <!-- 金额信息（订单模式 + 首单模式） -->
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="总金额(元)" prop="totalAmount">
            <el-input-number
              v-model="form.totalAmount"
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
        <el-col v-if="mode === 'order'" :span="8">
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
        <el-col v-if="mode === 'firstOrder'" :span="8">
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

      <!-- 餐数信息 -->
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
        <el-col v-if="mode === 'firstOrder'" :span="8">
          <el-form-item label="总价">
            <el-input-number
              v-model="form.totalAmount"
              :min="0"
              disabled
              :precision="2"
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 核销信息（仅订单模式） -->
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

      <!-- 日期信息（订单模式） -->
      <template v-if="mode === 'order'">
        <el-divider content-position="left">日期信息</el-divider>

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

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="订单开始日期">
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
            <el-form-item label="订单结束日期">
              <el-date-picker
                v-model="form.endDate"
                :disabled="readonly"
                type="date"
                placeholder="选择结束日期"
                style="width: 100%;"
                value-format="yyyy-MM-dd"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- 首单模式的日期选择（客户新增） -->
      <template v-if="mode === 'firstOrder'">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startDate">
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
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker
                v-model="form.endDate"
                :disabled="readonly"
                type="date"
                placeholder="选择结束日期"
                style="width: 100%;"
                value-format="yyyy-MM-dd"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </el-form>
  </div>
</template>

<script>
import * as profileApi from '@/api/customer/profile'
import * as categoryApi from '@/api/customer/packageCategory'
import * as dictDetailApi from '@/api/system/dictDetail'

// 订单模式默认数据
export function createOrderDefaultForm() {
  return {
    id: null,
    customerId: null,
    orderCode: null,
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
    endDate: null,
    status: 1,
    scheduleMode: 'SCHEDULE',
    deliveryDates: [],
    remark: null,
    customerSource: null
  }
}

// 首单模式默认数据
export function createFirstOrderDefaultForm() {
  return {
    parentPackageId: null,
    childPackageId: null,
    breakfastCount: 0,
    lunchDinnerCount: 0,
    totalCount: 0,
    breakfastPrice: 0,
    lunchDinnerPrice: 0,
    depositAmount: 0,
    totalAmount: 0,
    finalAmount: 0,
    scheduleMode: 'SCHEDULE',
    deliveryDates: [],
    startDate: null,
    endDate: null,
    customerSource: null
  }
}

export default {
  name: 'OrderForm',
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
      availableDates: []
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
    }
  },
  watch: {
    value: {
      handler(val) {
        // 当外部 value 变化时（如编辑时加载数据），同步内部数据
        // 套餐模式下，如果 parentPackageId 变化且有子套餐数据，需要同步 childPackages
        if (this.mode === 'firstOrder' && val.parentPackageId) {
          this.loadChildPackages(val.parentPackageId)
        }
      },
      immediate: true
    }
  },
  created() {
    this.loadCustomerSourceDict()
    this.initAvailableDates()
    if (this.mode === 'firstOrder') {
      this.loadParentPackages()
    }
    // 编辑时：如果传入了当前客户数据，填充下拉列表
    if (this.mode === 'order' && this.currentCustomer && this.currentCustomer.id) {
      this.customers = [{
        id: this.currentCustomer.id,
        customerName: this.currentCustomer.customerName || this.currentCustomer.name,
        phone: this.currentCustomer.phone
      }]
    }
  },
  methods: {
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
    // 客户选择变更
    onCustomerChange(customerId) {
      const customer = this.customers.find(c => c.id === customerId)
      if (customer) {
        this.form.customerName = customer.customerName
        this.form.phone = customer.phone
      }
      this.$emit('customer-change', customerId, customer)
    },
    // 餐数变更
    onMealCountChange() {
      if (this.mode === 'firstOrder') {
        this.form.totalCount = this.totalCount
        this.calcTotalAmount()
      }
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
    // 计算首单总价
    calcTotalAmount() {
      if (this.mode !== 'firstOrder') return
      const breakfastCount = this.form.breakfastCount || 0
      const lunchDinnerCount = this.form.lunchDinnerCount || 0
      const breakfastPrice = this.form.breakfastPrice || 0
      const lunchDinnerPrice = this.form.lunchDinnerPrice || 0
      const totalAmount = breakfastCount * breakfastPrice + lunchDinnerCount * lunchDinnerPrice
      this.form.totalAmount = totalAmount
      // 如果成交金额未填写，默认等于总价
      if (!this.form.finalAmount && totalAmount > 0) {
        this.form.finalAmount = totalAmount
      }
      this.$emit('calc-change')
    },
    // 父套餐变更
    async onParentPackageChange(parentId) {
      await this.loadChildPackages(parentId)
      this.form.childPackageId = null
    },
    // 子套餐变更
    onChildPackageChange(childPackageId) {
      this.$emit('package-change', this.form.parentPackageId, childPackageId)
    },
    // 加载父套餐列表
    async loadParentPackages() {
      try {
        const res = await categoryApi.getParents()
        this.parentPackages = (res.data || res) || []
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
        const res = await categoryApi.getTree()
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
    },
    // 获取表单校验引用
    validate() {
      // 由父组件调用 this.$refs.orderForm.validate()
      return new Promise((resolve, reject) => {
        this.$refs.elForm && this.$refs.elForm.validate((valid) => {
          if (valid) resolve(true)
          else reject(new Error('validation failed'))
        })
      })
    }
  }
}
</script>

<style scoped>
.order-form {
  width: 100%;
}
</style>
