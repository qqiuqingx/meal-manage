<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form ref="queryForm" :model="queryParams" :inline="true">
        <el-form-item label="客户名称" prop="customerName">
          <el-input
            v-model="queryParams.customerName"
            placeholder="请输入客户名称"
            clearable
            style="width: 140px;"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="订单ID" prop="orderId">
          <el-input
            v-model="queryParams.orderId"
            placeholder="请输入订单ID"
            clearable
            style="width: 120px;"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="餐次" prop="mealType">
          <el-select v-model="queryParams.mealType" placeholder="请选择餐次" clearable style="width: 100px;">
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
          </el-select>
        </el-form-item>
        <el-form-item label="排餐日期">
          <el-date-picker
            v-model="queryParams.recordDateStart"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="开始日期"
            clearable
            style="width: 140px;"
          />
          <span style="margin: 0 4px;">~</span>
          <el-date-picker
            v-model="queryParams.recordDateEnd"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="结束日期"
            clearable
            style="width: 140px;"
          />
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="queryParams.operateTimeStart"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="开始日期"
            clearable
            style="width: 140px;"
          />
          <span style="margin: 0 4px;">~</span>
          <el-date-picker
            v-model="queryParams.operateTimeEnd"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="结束日期"
            clearable
            style="width: 140px;"
          />
        </el-form-item>
        <el-form-item label="操作人" prop="operator">
          <el-input
            v-model="queryParams.operator"
            placeholder="请输入操作人"
            clearable
            style="width: 120px;"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never" style="margin-top: 12px;">
      <el-table v-loading="loading" :data="logList" border stripe>
        <el-table-column label="排餐日期" prop="recordDate" align="center" width="110">
          <template slot-scope="scope">
            {{ formatDate(scope.row.recordDate) }}
          </template>
        </el-table-column>
        <el-table-column label="餐次" prop="mealType" align="center" width="70">
          <template slot-scope="scope">
            <el-tag :type="scope.row.mealType === 'LUNCH' ? 'primary' : 'warning'" size="mini">
              {{ scope.row.mealType === 'LUNCH' ? '午餐' : '晚餐' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="客户名称" prop="customerName" align="center" width="90" />
        <el-table-column label="客户编码" prop="customerCode" align="center" width="90" />
        <el-table-column label="订单ID" prop="orderId" align="center" width="80" />
        <el-table-column label="套餐名称" prop="parentPackageName" align="center" width="110" />
        <el-table-column label="核销餐数" prop="verificationCount" align="center" width="80" />
        <el-table-column label="核销前剩余" prop="remainingBefore" align="center" width="90" />
        <el-table-column label="核销后剩余" prop="remainingAfter" align="center" width="90" />
        <el-table-column label="核销前已核" prop="verifiedTotalBefore" align="center" width="90" />
        <el-table-column label="核销后已核" prop="verifiedTotalAfter" align="center" width="90" />
        <el-table-column label="操作人" prop="operator" align="center" width="90" />
        <el-table-column label="操作时间" prop="operateTime" align="center" width="155">
          <template slot-scope="scope">
            {{ formatDateTime(scope.row.operateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" align="center" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="70" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" icon="el-icon-view" @click="handleDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        :page="queryParams.page + 1"
        :limit.sync="queryParams.size"
        @pagination="handlePagination"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog title="核销记录详情" :visible.sync="detailVisible" width="600px" append-to-body>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="排餐日期">{{ formatDate(detail.recordDate) }}</el-descriptions-item>
        <el-descriptions-item label="餐次">{{ detail.mealType === 'LUNCH' ? '午餐' : '晚餐' }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="客户编码">{{ detail.customerCode }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ detail.orderId }}</el-descriptions-item>
        <el-descriptions-item label="套餐名称">{{ detail.parentPackageName }}</el-descriptions-item>
        <el-descriptions-item label="核销餐数">{{ detail.verificationCount }}</el-descriptions-item>
        <el-descriptions-item label="核销前剩余">{{ detail.remainingBefore }}</el-descriptions-item>
        <el-descriptions-item label="核销后剩余">{{ detail.remainingAfter }}</el-descriptions-item>
        <el-descriptions-item label="核销前已核销">{{ detail.verifiedTotalBefore }}</el-descriptions-item>
        <el-descriptions-item label="核销后已核销">{{ detail.verifiedTotalAfter }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operator }}</el-descriptions-item>
        <el-descriptions-item label="操作时间" :span="2">{{ formatDateTime(detail.operateTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer">
        <el-button @click="detailVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { queryVerificationLogs } from '@/api/mealVerification'
import Pagination from '@/components/Pagination'

export default {
  name: 'VerificationLog',
  components: { Pagination },
  data() {
    return {
      loading: false,
      total: 0,
      logList: [],
      queryParams: {
        page: 0,
        size: 10,
        customerName: null,
        orderId: null,
        mealType: null,
        recordDateStart: null,
        recordDateEnd: null,
        operateTimeStart: null,
        operateTimeEnd: null,
        operator: null
      },
      detailVisible: false,
      detail: {}
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      const params = {}
      for (const key in this.queryParams) {
        if (this.queryParams[key] !== '' && this.queryParams[key] !== null && this.queryParams[key] !== undefined) {
          params[key] = this.queryParams[key]
        }
      }
      queryVerificationLogs(params).then(response => {
        this.logList = response.content || []
        this.total = response.totalElements || response.total || 0
      }).catch(() => {
        this.logList = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.page = 0
      this.getList()
    },
    resetQuery() {
      this.$refs.queryForm.resetFields()
      this.queryParams.recordDateStart = null
      this.queryParams.recordDateEnd = null
      this.queryParams.operateTimeStart = null
      this.queryParams.operateTimeEnd = null
      this.handleQuery()
    },
    handlePagination({ page, limit }) {
      this.queryParams.page = page - 1
      this.queryParams.size = limit
      this.getList()
    },
    handleDetail(row) {
      this.detail = row
      this.detailVisible = true
    },
    formatDate(val) {
      if (!val) return '—'
      const d = new Date(val)
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    },
    formatDateTime(val) {
      if (!val) return '—'
      const d = new Date(val)
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const h = String(d.getHours()).padStart(2, '0')
      const min = String(d.getMinutes()).padStart(2, '0')
      const s = String(d.getSeconds()).padStart(2, '0')
      return `${y}-${m}-${day} ${h}:${min}:${s}`
    }
  }
}
</script>

<style scoped>
.search-card {
  margin-bottom: 0;
}
.table-card {
  margin-top: 12px;
}
</style>
