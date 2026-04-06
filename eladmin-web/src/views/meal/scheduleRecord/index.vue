<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form ref="queryForm" :model="queryParams" :inline="true">
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="queryParams.startDate"
            type="date"
            placeholder="选择开始日期"
            value-format="yyyy-MM-dd"
            clearable
          />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="queryParams.endDate"
            type="date"
            placeholder="选择结束日期"
            value-format="yyyy-MM-dd"
            clearable
          />
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input
            v-model="queryParams.customerName"
            placeholder="请输入客户名称"
            clearable
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="餐次" prop="mealType">
          <el-select v-model="queryParams.mealType" placeholder="请选择餐次" clearable style="width: 160px;">
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表表格 -->
    <el-card class="table-card" shadow="never">
      <div slot="header" class="card-header">
        <span>排餐记录列表</span>
        <el-button
          type="success"
          icon="el-icon-s-promotion"
          size="small"
          @click="openGenerateDialog"
        >生成排餐计划</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="recordList"
        row-key="recordId"
        @expand-change="handleExpandChange"
      >
        <el-table-column type="expand">
          <template slot-scope="scope">
            <div v-loading="scope.row.loadingDetails" class="expand-wrapper">
              <el-table
                v-if="scope.row.customerMenus && scope.row.customerMenus.length"
                :data="scope.row.customerMenus"
                border
                size="mini"
                style="width: 90%; margin: 0 auto;"
                :span-method="(param) => dishSpanMethod(param, scope.row.customerMenus)"
              >
                <el-table-column label="排餐情况" prop="isReplaced" align="center" width="90">
                  <template slot-scope="inner">
                    <el-tag v-if="inner.row.isReplaced" type="warning" size="mini">新增替换</el-tag>
                    <el-tag v-else type="success" size="mini">常规排餐</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="菜品类型" prop="dishType" align="center" width="100">
                  <template slot-scope="inner">
                    <el-tag :type="dishTypeTag(inner.row.dishType)" size="mini">
                      {{ dishTypeMap[inner.row.dishType] || inner.row.dishType }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="菜品名称" prop="dishName" align="center" />
                <el-table-column label="配料" prop="dishIngredients" align="center" show-overflow-tooltip />
                <el-table-column label="人数" prop="dishCustomerCount" align="center" width="80">
                  <template slot-scope="inner">
                    <el-tag type="info" size="mini">{{ inner.row.dishCustomerCount }} 人</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="客户名称" prop="customerName" align="center" min-width="120" show-overflow-tooltip />
                <el-table-column label="替换原因" prop="replacementReason" align="center" show-overflow-tooltip />
              </el-table>
              <div v-else-if="!scope.row.loadingDetails" class="no-data">暂无客户菜单明细</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="排餐日期" prop="recordDate" align="center" min-width="130" />
        <el-table-column label="餐次" prop="mealType" align="center" width="90">
          <template slot-scope="scope">
            <el-tag :type="scope.row.mealType === 'LUNCH' ? 'primary' : 'warning'">
              {{ scope.row.mealType === 'LUNCH' ? '午餐' : '晚餐' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" align="center" width="100">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : (scope.row.status === 'FAILED' ? 'danger' : 'info')">
              {{ scope.row.status === 'SUCCESS' ? '成功' : (scope.row.status === 'FAILED' ? '失败' : '生成中') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="周数" prop="weekNum" align="center" width="80">
          <template slot-scope="scope">第{{ scope.row.weekNum }}周</template>
        </el-table-column>
        <el-table-column label="星期" prop="dayOfWeek" align="center" width="80">
          <template slot-scope="scope">
            {{ '周' + ['一', '二', '三', '四', '五', '六', '日'][scope.row.dayOfWeek - 1] }}
          </template>
        </el-table-column>
        <el-table-column label="客户数量" prop="customerCount" align="center" width="100">
          <template slot-scope="scope">
            <el-tag type="info">{{ scope.row.customerCount || 0 }} 人</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" min-width="180">
          <template slot-scope="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="200">
          <template slot-scope="scope">
            <el-button
              type="success"
              icon="el-icon-document"
              size="mini"
              title="查看生产单"
              @click="openProductionSheet(scope.row)"
            />
            <el-button
              type="primary"
              icon="el-icon-user"
              size="mini"
              title="管理客户排餐"
              @click="openCustomerDialog(scope.row)"
            />
            <el-button
              type="danger"
              icon="el-icon-delete"
              size="mini"
              title="删除此排餐"
              @click="handleDelete(scope.row)"
            />
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.page"
        :limit.sync="queryParams.size"
        @pagination="getList"
      />
    </el-card>

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
            <el-button type="text" style="color: #F56C6C;" @click="handleDeleteSingleCustomer(scope.row)">删除排餐</el-button>
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
  </div>
</template>

<script>
import { getMealPlanList, getMealPlanFullDetail, generateMealPlan, delMealPlan, delMealPlanCustomers } from '@/api/mealPlan'
import { getProfiles } from '@/api/customer/profile'
import { verifyMeal } from '@/api/mealVerification'

export default {
  name: 'ScheduleRecord',
  data() {
    return {
      loading: false,
      total: 0,
      recordList: [],
      queryParams: {
        page: 0,
        size: 10,
        startDate: null,
        endDate: null,
        customerName: null,
        mealType: null
      },
      dishTypeMap: {
        MAIN: '主菜',
        SIDE: '副菜',
        SOUP: '汤',
        VEGETABLE: '素菜',
        RICE: '米饭'
      },
      // 生成排餐计划
      generateDialog: {
        visible: false,
        loading: false
      },
      generateForm: {
        date: null,
        mealType: 'LUNCH',
        customerId: null
      },
      customerOptions: [],
      generateRules: {
        date: [{ required: true, message: '请选择排餐日期', trigger: 'change' }],
        mealType: [{ required: true, message: '请选择餐次', trigger: 'change' }]
      },
      // 客户排餐管理
      customerDialog: {
        visible: false,
        loading: false,
        list: [],
        selections: [],
        currentRecord: null
      },
      // 核销确认
      verifyDialog: {
        visible: false,
        loading: false,
        records: []
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      const params = { ...this.queryParams }
      if (!params.mealType) {
        delete params.mealType
      }
      getMealPlanList(params).then(res => {
        const list = res.content || []

        list.forEach(record => {
          record.recordId = record.id
          record.customerCount = record.totalCount
          record.createTime = record.generateTime

          if (record.recordDate) {
            const d = new Date(record.recordDate)
            const day = d.getDay()
            record.dayOfWeek = day === 0 ? 7 : day

            // Simple approach to get ISO week number
            const target = new Date(d.valueOf())
            const dayNr = (d.getDay() + 6) % 7
            target.setDate(target.getDate() - dayNr + 3)
            const firstThursday = target.valueOf()
            target.setMonth(0, 1)
            if (target.getDay() !== 4) {
              target.setMonth(0, 1 + ((4 - target.getDay()) + 7) % 7)
            }
            record.weekNum = 1 + Math.ceil((firstThursday - target) / 604800000)
          }

          // Placeholder for expansion
          record.customerMenus = null
          record.loadingDetails = false
        })

        this.recordList = list
        this.total = res.totalElements || 0
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.page = 0
      this.getList()
    },
    openProductionSheet(row) {
      const routeData = this.$router.resolve({
        path: '/meal/production-sheet',
        query: {
          mealPlanId: row.id,
          date: row.recordDate,
          mealType: row.mealType
        }
      })
      window.open(routeData.href, '_blank')
    },
    resetQuery() {
      this.$refs.queryForm.resetFields()
      this.queryParams.mealType = null
      this.handleQuery()
    },
    handleExpandChange(row, expandedRows) {
      const isExpanded = expandedRows.some(r => r.recordId === row.recordId)
      if (isExpanded && !row.customerMenus && !row.loadingDetails) {
        this.$set(row, 'loadingDetails', true)
        getMealPlanFullDetail(row.id).then(res => {
          const customers = res.customers || []
          const rawMenus = []
          if (customers.length > 0) {
            customers.forEach(customer => {
              if (customer.items) {
                customer.items.forEach(item => {
                  let ingredientsStr = ''
                  if (item.ingredients && item.ingredients.length > 0) {
                    ingredientsStr = item.ingredients.map(ing => ing.ingredientName).join('、')
                  }
                  rawMenus.push({
                    ...item,
                    customerName: customer.customerName,
                    dishIngredients: ingredientsStr,
                    replacementReason: item.replaceReason
                  })
                })
              }
            })

            const dishGroups = {}
            rawMenus.forEach(item => {
              const key = `${item.dishType}_${item.dishName}_${item.isReplaced}_${item.replacementReason || ''}`
              if (!dishGroups[key]) {
                dishGroups[key] = {
                  ...item,
                  customerNames: [item.customerName]
                }
              } else {
                if (!dishGroups[key].customerNames.includes(item.customerName)) {
                  dishGroups[key].customerNames.push(item.customerName)
                }
              }
            })

            const newMenus = Object.values(dishGroups).map(g => {
              return {
                ...g,
                customerName: g.customerNames.sort().join('、'),
                dishCustomerCount: g.customerNames.length
              }
            })

            const typeOrder = { 'SOUP': 1, 'MAIN': 2, 'SIDE': 3, 'VEGETABLE': 4, 'RICE': 5 }
            newMenus.sort((a, b) => {
              const replacedA = a.isReplaced ? 1 : 0
              const replacedB = b.isReplaced ? 1 : 0
              if (replacedA !== replacedB) return replacedA - replacedB
              return (typeOrder[a.dishType] || 99) - (typeOrder[b.dishType] || 99)
            })
            this.$set(row, 'customerMenus', newMenus)
          } else {
            this.$set(row, 'customerMenus', [])
          }
          this.$set(row, 'loadingDetails', false)
        }).catch(() => {
          this.$set(row, 'loadingDetails', false)
        })
      }
    },
    openGenerateDialog() {
      // 加载客户列表供选择
      getProfiles({ page: 0, size: 9999 }).then(res => {
        this.customerOptions = res.content || []
      })
      this.generateDialog.visible = true
    },
    resetGenerateForm() {
      this.$refs.generateForm && this.$refs.generateForm.resetFields()
      this.generateForm = { date: null, mealType: 'LUNCH', customerId: null }
    },
    handleGenerate() {
      this.$refs.generateForm.validate(valid => {
        if (!valid) return
        this.generateDialog.loading = true

        const data = {
          recordDate: this.generateForm.date,
          mealType: this.generateForm.mealType
        }
        // 如果选择了客户，则添加 customerId
        if (this.generateForm.customerId) {
          data.customerId = this.generateForm.customerId
        }

        generateMealPlan(data)
          .then(() => {
            this.$message.success(`${this.generateForm.date} 排餐计划生成成功！您可以刷新列表查看状态。`)
            this.generateDialog.visible = false
            this.getList()
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
    dishSpanMethod({ row, column, rowIndex, columnIndex }, customerMenus) {
      if (columnIndex === 0) {
        if (rowIndex === 0 || customerMenus[rowIndex - 1].isReplaced !== row.isReplaced) {
          let rowspan = 1
          for (let i = rowIndex + 1; i < customerMenus.length; i++) {
            if (customerMenus[i].isReplaced === row.isReplaced) {
              rowspan++
            } else {
              break
            }
          }
          return { rowspan, colspan: 1 }
        } else {
          return { rowspan: 0, colspan: 0 }
        }
      } else if (columnIndex === 4 || columnIndex === 5 || columnIndex === 6) {
        if (rowIndex === 0 ||
            customerMenus[rowIndex - 1].customerName !== row.customerName ||
            customerMenus[rowIndex - 1].isReplaced !== row.isReplaced ||
            customerMenus[rowIndex - 1].replacementReason !== row.replacementReason) {
          let rowspan = 1
          for (let i = rowIndex + 1; i < customerMenus.length; i++) {
            if (customerMenus[i].customerName === row.customerName &&
                customerMenus[i].isReplaced === row.isReplaced &&
                customerMenus[i].replacementReason === row.replacementReason) {
              rowspan++
            } else {
              break
            }
          }
          return { rowspan, colspan: 1 }
        } else {
          return { rowspan: 0, colspan: 0 }
        }
      }
    },
    handleDelete(row) {
      this.$confirm(`确认删除 ${row.recordDate} 的 ${row.mealType === 'LUNCH' ? '午餐' : '晚餐'} 排餐计划吗？将同时删除相关的明细数据！`, '危险操作', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error'
      }).then(() => {
        delMealPlan({ recordDate: row.recordDate, mealType: row.mealType }).then(() => {
          this.$message.success('删除成功')
          this.getList()
        })
      }).catch(() => {})
    },
    openCustomerDialog(row) {
      this.customerDialog.currentRecord = row
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
      if (this.customerDialog.selections.length === 0) return
      const ids = this.customerDialog.selections.map(item => item.id)
      this.$confirm(`确认删除选定的 ${ids.length} 个客户的排餐计划吗？将同时删除相关的明细数据！`, '危险操作', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error'
      }).then(() => {
        this.doDeleteCustomers(ids)
      }).catch(() => {})
    },
    handleDeleteSingleCustomer(row) {
      this.$confirm(`确认删除客户 "${row.customerName}" 的排餐计划吗？将同时删除相关的明细数据！`, '危险操作', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error'
      }).then(() => {
        this.doDeleteCustomers([row.id])
      }).catch(() => {})
    },
    doDeleteCustomers(ids) {
      delMealPlanCustomers(ids).then(() => {
        this.$message.success('删除成功')
        this.getList()
        this.fetchCustomerList()
        // 触发外层详情刷新
        if (this.customerDialog.currentRecord) {
          const record = this.customerDialog.currentRecord
          this.$set(record, 'customerMenus', null)
          this.handleExpandChange(record, [record])
        }
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
          this.getList()
          this.fetchCustomerList()
        })
        .catch(err => {
          const msg = err && err.response && err.response.data && err.response.data.message
          this.$message.error(msg || '核销失败，请重试')
        })
        .finally(() => {
          this.verifyDialog.loading = false
        })
    },
    dishTypeTag(type) {
      const map = {
        MAIN: 'danger',
        SIDE: 'warning',
        SOUP: 'info',
        VEGETABLE: 'success',
        RICE: ''
      }
      return map[type] || ''
    },
    formatTime(time) {
      if (!time) return '-'
      return time.replace('T', ' ').replace('Z', '').substring(0, 19)
    }
  }
}
</script>

<style scoped>
.search-card {
  margin-bottom: 15px;
}
.table-card {
  margin-bottom: 15px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.expand-wrapper {
  padding: 10px 0;
}
.no-data {
  text-align: center;
  color: #909399;
  padding: 16px 0;
  font-size: 13px;
}
</style>
