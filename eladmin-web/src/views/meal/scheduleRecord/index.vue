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
        <el-form-item label="餐次" prop="mealTypes">
          <el-select v-model="queryParams.mealTypes" placeholder="请选择餐次" clearable multiple style="width: 160px;">
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
      >
        <el-table-column type="expand">
          <template slot-scope="scope">
            <div class="expand-wrapper">
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
                <el-table-column label="客户名称" prop="customerName" align="center" min-width="120" />
                <el-table-column label="替换原因" prop="replacementReason" align="center" show-overflow-tooltip />
              </el-table>
              <div v-else class="no-data">暂无客户菜单明细</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="排餐日期" prop="recordDate" align="center" width="130" />
        <el-table-column label="餐次" prop="mealType" align="center" width="90">
          <template slot-scope="scope">
            <el-tag :type="scope.row.mealType === 'LUNCH' ? 'primary' : 'warning'">
              {{ scope.row.mealType === 'LUNCH' ? '午餐' : '晚餐' }}
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
            <el-tag type="info">{{ scope.row.customerCount }} 人</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="180">
          <template slot-scope="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90">
          <template slot-scope="scope">
            <el-button
              type="danger"
              icon="el-icon-delete"
              size="mini"
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
            <el-option label="全部" value="ALL" />
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户ID" prop="customerId">
          <el-input
            v-model.number="generateForm.customerId"
            placeholder="不填则为所有生效客户排餐"
            clearable
            type="number"
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="generateDialog.visible = false">取 消</el-button>
        <el-button type="primary" :loading="generateDialog.loading" @click="handleGenerate">确认生成</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { queryScheduleList, generateSchedule, delSchedule } from '@/api/dish'

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
        mealTypes: []
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
        mealType: 'ALL',
        customerId: null
      },
      generateRules: {
        date: [{ required: true, message: '请选择排餐日期', trigger: 'change' }],
        mealType: [{ required: true, message: '请选择餐次', trigger: 'change' }]
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
      if (params.mealTypes && params.mealTypes.length === 0) {
        delete params.mealTypes
      }
      queryScheduleList(params).then(res => {
        const list = res.content || []

        // 优化相同菜品的展示：将具有完全相同菜品的记录合并，把客户名称以顿号隔开
        list.forEach(record => {
          if (!record.customerMenus || !record.customerMenus.length) return
          // 按 (dishType, dishName, isReplaced, replacementReason) 分组
          // 不含 dishIngredients，避免同一道菜因配料字段不一致（如 NULL vs 有值）被拆成多条
          const dishGroups = {}

          record.customerMenus.forEach(item => {
            const key = `${item.dishType}_${item.dishName}_${item.isReplaced}_${item.replacementReason || ''}`
            if (!dishGroups[key]) {
              dishGroups[key] = {
                ...item,
                customerNames: [item.customerName],
                // 取第一个非空的 dishIngredients 作为展示值
                dishIngredients: item.dishIngredients || ''
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
              customerName: g.customerNames.sort().join('、')
            }
          })

          const typeOrder = { 'SOUP': 1, 'MAIN': 2, 'SIDE': 3, 'VEGETABLE': 4, 'RICE': 5 }
          newMenus.sort((a, b) => {
            // 优先按照是否替换排序（常规排餐在前，新增替换的在后）
            const replacedA = a.isReplaced ? 1 : 0
            const replacedB = b.isReplaced ? 1 : 0
            if (replacedA !== replacedB) return replacedA - replacedB
            // 同一排餐情况内，按照菜品类型排序
            return (typeOrder[a.dishType] || 99) - (typeOrder[b.dishType] || 99)
          })
          record.customerMenus = newMenus
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
    resetQuery() {
      this.$refs.queryForm.resetFields()
      this.queryParams.mealTypes = []
      this.handleQuery()
    },
    openGenerateDialog() {
      this.generateDialog.visible = true
    },
    resetGenerateForm() {
      this.$refs.generateForm && this.$refs.generateForm.resetFields()
      this.generateForm = { date: null, mealType: 'ALL', customerId: null }
    },
    handleGenerate() {
      this.$refs.generateForm.validate(valid => {
        if (!valid) return
        this.generateDialog.loading = true
        const queryParams = { mealType: this.generateForm.mealType }
        if (this.generateForm.customerId) {
          queryParams.customerId = this.generateForm.customerId
        }
        generateSchedule(this.generateForm.date, queryParams)
          .then(() => {
            this.$message.success(`${this.generateForm.date} 排餐计划生成成功！`)
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
      }
    },
    handleDelete(row) {
      this.$confirm('确认删除该排餐记录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        delSchedule(row.recordId).then(() => {
          this.$message.success('删除成功')
          this.getList()
        })
      }).catch(() => {})
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
