<template>
  <el-dialog
    :visible.sync="dialogVisible"
    :title="dialogTitle"
    width="1200px"
    top="5vh"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-loading="loading">
      <!-- 客户基本信息 -->
      <el-card class="info-card" shadow="never">
        <div slot="header" class="card-header">
          <span class="card-title">客户基本信息</span>
        </div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="客户编号">{{ customer.customerCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户姓名">{{ customer.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ customer.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="孕周">{{ customer.gestationalWeek ? customer.gestationalWeek + '周' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ customer.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="过敏食物" :span="3">
            <el-tag
              v-for="(tag, index) in customer.allergyTags"
              :key="index"
              size="small"
              type="warning"
              style="margin-right: 5px;"
            >
              {{ tag }}
            </el-tag>
            <span v-if="!customer.allergyTags || customer.allergyTags.length === 0">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="医嘱要求" :span="3">
            {{ customer.medicalRequirements || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="默认地址" :span="3">
            {{ customer.defaultAddress || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">
            {{ customer.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 订单列表 -->
      <el-card class="info-card" shadow="never" style="margin-top: 15px;">
        <div slot="header" class="card-header">
          <span class="card-title">订单信息</span>
        </div>

        <el-table
          :data="orderList"
          :loading="orderLoading"
          border
          stripe
          size="small"
        >
          <el-table-column label="订单编号" prop="orderCode" width="140" show-overflow-tooltip />
          <el-table-column label="父套餐" width="120" show-overflow-tooltip>
            <template slot-scope="scope">
              {{ parentPackageMap[scope.row.parentPackageId] || scope.row.parentPackageName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="子套餐" width="120" show-overflow-tooltip>
            <template slot-scope="scope">
              {{ subPackageMap[scope.row.childPackageId] || scope.row.childPackageName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="总金额" prop="totalAmount" width="90" align="right" :formatter="amountFormatter" />
          <el-table-column label="成交金额" prop="finalAmount" width="100" align="right" :formatter="amountFormatter" />
          <el-table-column label="早餐" prop="breakfastCount" width="60" align="center" />
          <el-table-column label="午晚" prop="lunchDinnerCount" width="60" align="center" />
          <el-table-column label="合计" prop="totalCount" width="60" align="center" />
          <el-table-column label="已核销" prop="verifiedCount" width="60" align="center" />
          <el-table-column label="剩余" prop="remainingCount" width="60" align="center" />
          <el-table-column label="状态" prop="statusDesc" width="80" align="center">
            <template slot-scope="scope">
              <el-tag
                :type="getStatusType(scope.row.status)"
                size="small"
              >
                {{ scope.row.statusDesc || getStatusDesc(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="餐次" width="70" align="center">
            <template slot-scope="scope">
              {{ orderMealTypeText(scope.row.mealType) }}
            </template>
          </el-table-column>
          <el-table-column label="订单期间" width="180" show-overflow-tooltip>
            <template slot-scope="scope">
              {{ scope.row.startDate }} ~ {{ scope.row.endDate }}
            </template>
          </el-table-column>
          <el-table-column label="送餐日期" width="180" show-overflow-tooltip>
            <template slot-scope="scope">
              {{ formatDeliveryDates(scope.row.deliveryDates) }}
            </template>
          </el-table-column>
          <el-table-column label="成交时间" prop="dealTime" width="150" show-overflow-tooltip />
        </el-table>

        <!-- 订单为空时显示 -->
        <div v-if="!orderLoading && (!orderList || orderList.length === 0)" class="empty-tips">
          <el-empty description="暂无订单数据" :image-size="80" />
        </div>

        <!-- 分页 -->
        <el-pagination
          v-if="orderTotal > 0"
          :current-page="orderPage"
          :page-sizes="[10, 20, 50, 100]"
          :page-size="orderSize"
          :total="orderTotal"
          layout="total, sizes, prev, pager, next, jumper"
          style="margin-top: 15px; text-align: right;"
          @size-change="handleOrderSizeChange"
          @current-change="handleOrderPageChange"
        />
      </el-card>
    </div>

    <div slot="footer" class="dialog-footer">
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" @click="handleEditCustomer">编辑客户</el-button>
      <el-button @click="handleViewAllOrders">查看全部订单</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { getOrdersByCustomer } from '@/api/customer/order'
import * as packageApi from '@/api/customer/package'

export default {
  name: 'CustomerDetailDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    customer: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      loading: false,
      orderLoading: false,
      orderList: [],
      packageTree: [],
      orderPage: 1,
      orderSize: 10,
      orderTotal: 0
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible
      },
      set(val) {
        this.$emit('update:visible', val)
      }
    },
    dialogTitle() {
      return this.customer && this.customer.customerName
        ? `客户详情 - ${this.customer.customerName}`
        : '客户详情'
    },
    parentPackageMap() {
      const map = {}
      for (const parent of this.packageTree) {
        map[parent.id] = parent.packageName
      }
      return map
    },
    subPackageMap() {
      const map = {}
      for (const parent of this.packageTree) {
        if (parent.children) {
          for (const sub of parent.children) {
            map[sub.id] = sub.subPackageName
          }
        }
      }
      return map
    }
  },
  watch: {
    visible(newVal) {
      if (newVal && this.customer && this.customer.id) {
        this.loadOrderList()
      }
    }
  },
  created() {
    this.loadPackageTree()
  },
  methods: {
    async loadPackageTree() {
      try {
        const res = await packageApi.getTree()
        this.packageTree = res.data || res || []
      } catch (e) {
        console.error('loadPackageTree error', e)
      }
    },
    async loadOrderList() {
      if (!this.customer || !this.customer.id) {
        return
      }
      this.orderLoading = true
      try {
        const res = await getOrdersByCustomer(this.customer.id, {
          page: this.orderPage,
          size: this.orderSize
        })
        this.orderList = res.content || res.data || []
        this.orderTotal = res.totalElements || res.total || 0
      } catch (e) {
        console.error('loadOrderList error', e)
        this.$message.error('加载订单列表失败')
      } finally {
        this.orderLoading = false
      }
    },
    handleOrderSizeChange(val) {
      this.orderSize = val
      this.orderPage = 1
      this.loadOrderList()
    },
    handleOrderPageChange(val) {
      this.orderPage = val
      this.loadOrderList()
    },
    amountFormatter(row, column, cellValue) {
      if (cellValue === null || cellValue === undefined) {
        return '-'
      }
      return '¥' + Number(cellValue).toFixed(2)
    },
    getStatusType(status) {
      if (status === null) return 'info'
      switch (status) {
        case 0: return 'danger'
        case 1: return 'warning'
        case 2: return 'success'
        default: return 'info'
      }
    },
    getStatusDesc(status) {
      if (status === null) return '未知'
      switch (status) {
        case 0: return '已取消'
        case 1: return '进行中'
        case 2: return '已完成'
        default: return '未知'
      }
    },
    formatDeliveryDates(value) {
      if (!value) return '-'
      if (Array.isArray(value)) {
        return value.length ? value.join(', ') : '-'
      }
      if (typeof value === 'string') {
        try {
          const parsed = JSON.parse(value)
          return Array.isArray(parsed) && parsed.length ? parsed.join(', ') : '-'
        } catch (e) {
          return value
        }
      }
      return '-'
    },
    orderMealTypeText(mealType) {
      if (!mealType || mealType === 'ALL') return '-'
      return mealType === 'LUNCH' ? '午餐' : '晚餐'
    },
    handleClose() {
      this.dialogVisible = false
    },
    handleEditCustomer() {
      this.$emit('edit-customer', this.customer)
      this.dialogVisible = false
    },
    handleViewAllOrders() {
      this.$emit('view-all-orders', this.customer)
    }
  }
}
</script>

<style scoped>
.info-card {
  margin-bottom: 15px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.empty-tips {
  padding: 40px 0;
  text-align: center;
}

.dialog-footer {
  text-align: right;
}

.el-descriptions :deep(.el-descriptions-item__label) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
</style>
