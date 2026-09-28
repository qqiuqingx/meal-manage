<template>
  <div class="app-container">
    <!--工具栏-->
    <div class="head-container">
      <div v-if="crud.props.searchToggle">
        <el-input v-model="query.orderCode" clearable size="small" placeholder="订单编号" style="width: 150px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.customerCode" clearable size="small" placeholder="客户编号" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-input v-model="query.customerName" clearable size="small" placeholder="客户姓名" style="width: 120px;" class="filter-item" @keyup.enter.native="crud.toQuery" />
        <el-select v-model="query.status" clearable size="small" placeholder="订单状态" class="filter-item" style="width: 100px" @change="crud.toQuery">
          <el-option label="进行中" :value="1" />
          <el-option label="已完成" :value="2" />
          <el-option label="已取消" :value="0" />
          <el-option label="已退餐" :value="3" />
          <el-option label="暂停" :value="4" />
        </el-select>
        <el-select v-model="query.customerSource" clearable size="small" placeholder="销售渠道" class="filter-item" style="width: 120px" @change="crud.toQuery">
          <el-option v-for="item in customerSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-date-picker
          v-model="query.scheduleDate"
          type="date"
          size="small"
          placeholder="排餐日期"
          class="filter-item"
          style="width: 150px"
          value-format="yyyy-MM-dd"
          clearable
          @change="crud.toQuery"
        />
        <rrOperation />
      </div>
      <crudOperation :permission="permission" />
    </div>

    <!--表格渲染-->
    <el-table
      ref="table"
      v-loading="crud.loading"
      :data="crud.data"
      @selection-change="crud.selectionChangeHandler"
    >
      <el-table-column :selectable="checkboxT" type="selection" width="55" />
      <el-table-column label="客户编号" prop="customerCode" width="130" fixed="left">
        <template slot-scope="scope">
          <el-input
            v-if="isInlineEditing(scope.row, 'customerCode')"
            :ref="inlineInputRef(scope.row, 'customerCode')"
            :value="getInlineDraft(scope.row, 'customerCode')"
            :disabled="isInlineBusy(scope.row)"
            size="mini"
            @input="setInlineDraft(scope.row, 'customerCode', $event)"
            @blur="submitInlineDraft(scope.row, 'customerCode')"
            @mouseleave.native="submitInlineDraft(scope.row, 'customerCode')"
            @keyup.enter.native="submitInlineDraft(scope.row, 'customerCode')"
            @keyup.esc.native="cancelInlineDraft(scope.row, 'customerCode', $event)"
          />
          <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'customerCode')">{{ scope.row.customerCode || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="客户姓名" prop="customerName" width="100" />
      <el-table-column label="特殊要求" prop="specialRequirements" min-width="140" show-overflow-tooltip>
        <template slot-scope="scope">
          <el-input
            v-if="isInlineEditing(scope.row, 'specialRequirements')"
            :ref="inlineInputRef(scope.row, 'specialRequirements')"
            :value="getInlineDraft(scope.row, 'specialRequirements')"
            :disabled="isInlineBusy(scope.row)"
            size="mini"
            placeholder="特殊要求"
            @input="setInlineDraft(scope.row, 'specialRequirements', $event)"
            @blur="submitInlineDraft(scope.row, 'specialRequirements')"
            @mouseleave.native="submitInlineDraft(scope.row, 'specialRequirements')"
            @keyup.enter.native="submitInlineDraft(scope.row, 'specialRequirements')"
            @keyup.esc.native="cancelInlineDraft(scope.row, 'specialRequirements', $event)"
          />
          <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'specialRequirements')">{{ scope.row.specialRequirements || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="手机号" prop="phone" width="145">
        <template slot-scope="scope">
          <el-input
            v-if="isInlineEditing(scope.row, 'phone')"
            :ref="inlineInputRef(scope.row, 'phone')"
            :value="getInlineDraft(scope.row, 'phone')"
            :disabled="isInlineBusy(scope.row)"
            size="mini"
            maxlength="11"
            @input="setInlineDraft(scope.row, 'phone', $event)"
            @blur="submitInlineDraft(scope.row, 'phone')"
            @mouseleave.native="submitInlineDraft(scope.row, 'phone')"
            @keyup.enter.native="submitInlineDraft(scope.row, 'phone')"
            @keyup.esc.native="cancelInlineDraft(scope.row, 'phone', $event)"
          />
          <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'phone')">{{ scope.row.phone || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="地址" min-width="220">
        <template slot-scope="scope">
          <div v-if="!scope.row.addresses || scope.row.addresses.length === 0">-</div>
          <div v-for="addr in scope.row.addresses" :key="addr.addressType" class="inline-address-row">
            <template v-if="isInlineEditing(scope.row, addressInlineField(addr))">
              <span>{{ addr.type }}:</span>
              <el-input
                :ref="inlineInputRef(scope.row, addressInlineField(addr))"
                :value="getInlineDraft(scope.row, addressInlineField(addr))"
                :disabled="isInlineBusy(scope.row)"
                size="mini"
                maxlength="200"
                @input="setInlineDraft(scope.row, addressInlineField(addr), $event)"
                @blur="submitInlineDraft(scope.row, addressInlineField(addr))"
                @mouseleave.native="submitInlineDraft(scope.row, addressInlineField(addr))"
                @keyup.enter.native="submitInlineDraft(scope.row, addressInlineField(addr))"
                @keyup.esc.native="cancelInlineDraft(scope.row, addressInlineField(addr), $event)"
              />
            </template>
            <el-tag v-else size="mini" disable-transitions :class="{ 'is-editable': isInlineEditable(scope.row) }" @click.native="beginInlineEdit(scope.row, addressInlineField(addr))">
              {{ addr.type }}: {{ addr.detail }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="规格" width="180">
        <template slot-scope="scope">
          <div class="inline-spec-field">
            <span>主</span>
            <el-input
              v-if="isInlineEditing(scope.row, 'mainDishCount')"
              :ref="inlineInputRef(scope.row, 'mainDishCount')"
              :value="getInlineDraft(scope.row, 'mainDishCount')"
              :disabled="isInlineBusy(scope.row)"
              size="mini"
              type="text"
              inputmode="numeric"
              @input="setInlineDraft(scope.row, 'mainDishCount', $event)"
              @blur="submitInlineDraft(scope.row, 'mainDishCount')"
              @mouseleave.native="submitInlineDraft(scope.row, 'mainDishCount')"
              @keyup.enter.native="submitInlineDraft(scope.row, 'mainDishCount')"
              @keyup.esc.native="cancelInlineDraft(scope.row, 'mainDishCount', $event)"
            />
            <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'mainDishCount')">{{ scope.row.mainDishCount == null ? 0 : scope.row.mainDishCount }}</span>
            <span>副</span>
            <el-input
              v-if="isInlineEditing(scope.row, 'sideDishCount')"
              :ref="inlineInputRef(scope.row, 'sideDishCount')"
              :value="getInlineDraft(scope.row, 'sideDishCount')"
              :disabled="isInlineBusy(scope.row)"
              size="mini"
              type="text"
              inputmode="numeric"
              @input="setInlineDraft(scope.row, 'sideDishCount', $event)"
              @blur="submitInlineDraft(scope.row, 'sideDishCount')"
              @mouseleave.native="submitInlineDraft(scope.row, 'sideDishCount')"
              @keyup.enter.native="submitInlineDraft(scope.row, 'sideDishCount')"
              @keyup.esc.native="cancelInlineDraft(scope.row, 'sideDishCount', $event)"
            />
            <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'sideDishCount')">{{ scope.row.sideDishCount == null ? 0 : scope.row.sideDishCount }}</span>
            <span>素</span>
            <el-input
              v-if="isInlineEditing(scope.row, 'vegCount')"
              :ref="inlineInputRef(scope.row, 'vegCount')"
              :value="getInlineDraft(scope.row, 'vegCount')"
              :disabled="isInlineBusy(scope.row)"
              size="mini"
              type="text"
              inputmode="numeric"
              @input="setInlineDraft(scope.row, 'vegCount', $event)"
              @blur="submitInlineDraft(scope.row, 'vegCount')"
              @mouseleave.native="submitInlineDraft(scope.row, 'vegCount')"
              @keyup.enter.native="submitInlineDraft(scope.row, 'vegCount')"
              @keyup.esc.native="cancelInlineDraft(scope.row, 'vegCount', $event)"
            />
            <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'vegCount')">{{ scope.row.vegCount == null ? 0 : scope.row.vegCount }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="含汤" width="70" align="center">
        <template slot-scope="scope">
          <el-select
            v-if="isInlineEditing(scope.row, 'soupCount')"
            :ref="inlineInputRef(scope.row, 'soupCount')"
            :value="scope.row.soupCount"
            :disabled="isInlineBusy(scope.row)"
            size="mini"
            @change="saveInlineSelection(scope.row, 'soupCount', $event)"
            @visible-change="!$event && cancelInlineDraft(scope.row, 'soupCount')"
          >
            <el-option label="含汤" :value="1" />
            <el-option label="不含" :value="0" />
          </el-select>
          <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'soupCount')">{{ scope.row.soupCount >= 1 ? '含汤' : '不含汤' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="排餐模式" width="100">
        <template slot-scope="scope">
          <el-select
            v-if="isInlineEditing(scope.row, 'scheduleMode')"
            :ref="inlineInputRef(scope.row, 'scheduleMode')"
            :value="scope.row.scheduleMode"
            :disabled="isInlineBusy(scope.row)"
            size="mini"
            @change="saveInlineSelection(scope.row, 'scheduleMode', $event)"
            @visible-change="!$event && cancelInlineDraft(scope.row, 'scheduleMode')"
          >
            <el-option label="指定日期" value="SCHEDULE" />
            <el-option label="每天送" value="DAILY" />
            <el-option label="周末送" value="WEEKEND" />
            <el-option label="工作日" value="WEEKDAY" />
          </el-select>
          <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'scheduleMode')">{{ scheduleModeText(scope.row.scheduleMode) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="自定义菜单" width="120" align="center">
        <template slot-scope="scope">
          <div
            v-if="scope.row.customMenuImage || isInlineEditable(scope.row)"
            class="inline-menu-cell"
            @click="openCustomMenuDialog(scope.row)"
          >
            <el-image
              v-if="scope.row.customMenuImage"
              :src="getCustomMenuImageUrl(scope.row.customMenuImage)"
              fit="contain"
              style="width: 40px; height: 40px;"
            />
            <span v-else class="inline-value is-editable">上传菜单</span>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="过敏" width="190">
        <template slot-scope="scope">
          <div v-if="isInlineEditing(scope.row, 'allergyTags')" class="inline-allergy-cell" @mouseleave="cancelInlineDraft(scope.row, 'allergyTags')">
            <el-tag
              v-for="(tag, index) in (scope.row.allergyTags || [])"
              :key="`${tag}-${index}`"
              size="mini"
              type="warning"
              closable
              :disable-transitions="true"
              @close="removeAllergyTag(scope.row, tag)"
            >{{ tag }}</el-tag>
            <div class="inline-allergy-input">
              <el-input
                :value="getAllergyDraft(scope.row)"
                :disabled="isInlineBusy(scope.row)"
                size="mini"
                placeholder="添加标签"
                @input="setAllergyDraft(scope.row, $event)"
                @keyup.enter.native="addAllergyTag(scope.row)"
              />
              <el-button size="mini" type="text" :disabled="isInlineBusy(scope.row)" @click="addAllergyTag(scope.row)">添加</el-button>
              <el-button size="mini" type="text" @click="cancelInlineDraft(scope.row, 'allergyTags')">完成</el-button>
            </div>
          </div>
          <div v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'allergyTags')">
            <span v-if="!scope.row.allergyTags || scope.row.allergyTags.length === 0">-</span>
            <el-tag v-for="(tag, index) in (scope.row.allergyTags || [])" :key="`${tag}-${index}`" size="mini" type="warning" style="margin-right: 4px;">
              {{ tag }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="早餐" prop="breakfastCount" width="90" align="center">
        <template slot-scope="scope">
          <el-input
            v-if="isInlineEditing(scope.row, 'breakfastCount')"
            :ref="inlineInputRef(scope.row, 'breakfastCount')"
            :value="getInlineDraft(scope.row, 'breakfastCount')"
            :disabled="isInlineBusy(scope.row)"
            size="mini"
            type="text"
            inputmode="numeric"
            @input="setInlineDraft(scope.row, 'breakfastCount', $event)"
            @blur="submitInlineDraft(scope.row, 'breakfastCount')"
            @mouseleave.native="submitInlineDraft(scope.row, 'breakfastCount')"
            @keyup.enter.native="submitInlineDraft(scope.row, 'breakfastCount')"
            @keyup.esc.native="cancelInlineDraft(scope.row, 'breakfastCount', $event)"
          />
          <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'breakfastCount')">{{ scope.row.breakfastCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="午晚" prop="lunchDinnerCount" width="90" align="center">
        <template slot-scope="scope">
          <el-input
            v-if="isInlineEditing(scope.row, 'lunchDinnerCount')"
            :ref="inlineInputRef(scope.row, 'lunchDinnerCount')"
            :value="getInlineDraft(scope.row, 'lunchDinnerCount')"
            :disabled="isInlineBusy(scope.row)"
            size="mini"
            type="text"
            inputmode="numeric"
            @input="setInlineDraft(scope.row, 'lunchDinnerCount', $event)"
            @blur="submitInlineDraft(scope.row, 'lunchDinnerCount')"
            @mouseleave.native="submitInlineDraft(scope.row, 'lunchDinnerCount')"
            @keyup.enter.native="submitInlineDraft(scope.row, 'lunchDinnerCount')"
            @keyup.esc.native="cancelInlineDraft(scope.row, 'lunchDinnerCount', $event)"
          />
          <span v-else class="inline-value" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click="beginInlineEdit(scope.row, 'lunchDinnerCount')">{{ scope.row.lunchDinnerCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="合计" prop="totalCount" width="60" align="center" />
      <el-table-column label="核销" prop="verifiedCount" width="60" align="center">
        <template slot-scope="scope">
          <el-tooltip
            v-if="scope.row.importedVerifiedCount > 0"
            :content="`含导入前已核销 ${scope.row.importedVerifiedCount} 餐`"
            placement="top"
          >
            <span>{{ scope.row.verifiedCount }}</span>
          </el-tooltip>
          <span v-else>{{ scope.row.verifiedCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="已排餐" prop="scheduledCount" width="80" align="center" />
      <el-table-column prop="remainingCount" width="70" align="center">
        <template slot="header">
          <span class="column-help">
            剩余
            <el-tooltip content="当前订单剩余餐数，已扣减所有已核销餐数。" placement="top">
              <i class="el-icon-question" />
            </el-tooltip>
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="estimatedRemainingCount" width="120" align="center">
        <template slot="header">
          <span class="column-help">
            预计剩余餐数
            <el-tooltip content="预计剩余餐数 = 剩余餐数 - 今天已排餐但未核销餐数；今天已核销的餐不重复扣减。" placement="top">
              <i class="el-icon-question" />
            </el-tooltip>
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="canViewAmount" label="余额" prop="mealBalance" width="90" align="right">
        <template slot-scope="scope">
          {{ formatMoney(scope.row.mealBalance) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template slot-scope="scope">
          <el-select
            v-if="isInlineEditing(scope.row, 'status')"
            :ref="inlineInputRef(scope.row, 'status')"
            :value="scope.row.status"
            :disabled="isInlineBusy(scope.row)"
            size="mini"
            @change="saveInlineSelection(scope.row, 'status', $event)"
            @visible-change="!$event && cancelInlineDraft(scope.row, 'status')"
          >
            <el-option label="进行中" :value="1" />
            <el-option label="暂停" :value="4" />
          </el-select>
          <el-tag v-else :type="statusTagType(scope.row.status)" :class="{ 'is-editable': isInlineEditable(scope.row) }" @click.native="beginInlineEdit(scope.row, 'status')">
            {{ statusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="餐次" width="70" align="center">
        <template slot-scope="scope">
          {{ mealTypeText(scope.row.mealType, scope.row.status) }}
        </template>
      </el-table-column>
      <el-table-column label="销售渠道" width="100">
        <template slot-scope="scope">
          {{ getSourceLabel(scope.row.customerSource) }}
        </template>
      </el-table-column>
      <el-table-column label="订单期间" width="180">
        <template slot-scope="{ row }">
          {{ formatOrderPeriod(row) }}
        </template>
      </el-table-column>
      <el-table-column label="成交时间" prop="dealTime" width="150" />
      <el-table-column v-if="canViewAmount" label="定金" prop="depositAmount" width="90" align="right">
        <template slot-scope="scope">
          {{ formatMoney(scope.row.depositAmount) }}
        </template>
      </el-table-column>
      <el-table-column v-if="canViewAmount" label="总金额" prop="totalAmount" width="100" align="right">
        <template slot-scope="scope">
          {{ formatMoney(scope.row.totalAmount) }}
        </template>
      </el-table-column>
      <el-table-column v-if="canViewAmount" label="成交金额" prop="finalAmount" width="100" align="right">
        <template slot-scope="scope">
          {{ formatMoney(scope.row.finalAmount) }}
        </template>
      </el-table-column>
      <el-table-column label="订单编号" prop="orderCode" width="140" />
      <el-table-column v-if="checkPer(['admin','customerOrder:edit','customerOrder:del'])" label="操作" width="180" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" icon="edit" :disabled="scope.row.status !== 1 && scope.row.status !== 4" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button v-if="scope.row.status === 1 || scope.row.status === 4" size="mini" type="danger" icon="refresh" @click="openRefundDialog(scope.row)">退餐</el-button>
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

    <el-dialog title="自定义菜单" :visible.sync="menuDialogVisible" width="420px" append-to-body :close-on-click-modal="false" @closed="menuDialogRow = null">
      <div v-if="menuDialogRow" class="menu-dialog-body">
        <el-image
          v-if="menuDialogRow.customMenuImage"
          :src="getCustomMenuImageUrl(menuDialogRow.customMenuImage)"
          :preview-src-list="[getCustomMenuImageUrl(menuDialogRow.customMenuImage)]"
          fit="contain"
          class="menu-dialog-image"
        />
        <div v-else class="menu-dialog-empty">尚未上传菜单图片</div>
        <div v-if="isInlineEditable(menuDialogRow)" class="menu-dialog-actions">
          <el-upload
            :action="imagesUploadApi"
            :headers="uploadHeaders"
            :disabled="isInlineBusy(menuDialogRow)"
            :show-file-list="false"
            :before-upload="getCustomMenuBeforeUpload(menuDialogRow)"
            :on-success="getCustomMenuUploadSuccess(menuDialogRow)"
            :on-error="getCustomMenuUploadError(menuDialogRow)"
            accept="image/*"
          >
            <el-button type="primary" size="small" :loading="isInlineBusy(menuDialogRow)">
              {{ menuDialogRow.customMenuImage ? '替换菜单图片' : '上传菜单图片' }}
            </el-button>
          </el-upload>
          <el-button v-if="menuDialogRow.customMenuImage" size="small" :disabled="isInlineBusy(menuDialogRow)" @click="removeCustomMenuImage">删除图片</el-button>
        </div>
        <div v-if="isInlineEditable(menuDialogRow)" class="menu-dialog-hint">支持图片文件，大小不超过 5MB</div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="menuDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <!--表单组件-->
    <el-dialog
      ref="dialogRef"
      append-to-body
      :close-on-click-modal="false"
      :before-close="handleDialogClose"
      :visible.sync="dialogVisible"
      :title="dialogTitle"
      width="900px"
      top="5vh"
    >
      <el-form ref="form" :model="form" :rules="rules" size="small" label-width="110px">
        <OrderForm
          ref="orderFormRef"
          v-model="form"
          mode="order"
          :readonly="false"
          :customer-disabled="!!form.id"
          :current-customer="{ id: form.customerId, customerCode: form.customerCode, customerName: form.customerName, phone: form.phone }"
          :show-amount="canViewAmount"
          :editable-amount="canEditAmount"
          :rules="rules"
          @customer-change="onCustomerChange"
          @calc-change="onCalcChange"
        />

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

    <!--退餐对话框-->
    <el-dialog title="退餐" :visible.sync="refundDialogVisible" width="500px" append-to-body :close-on-click-modal="false">
      <el-form ref="refundFormRef" :model="refundForm" :rules="refundRules" size="small" label-width="90px">
        <el-form-item label="订单编号">
          <span>{{ refundForm.orderCode }}</span>
        </el-form-item>
        <el-form-item label="客户姓名">
          <span>{{ refundForm.customerName }}</span>
        </el-form-item>
        <el-form-item v-if="canViewAmount" label="退餐金额">
          <span>¥{{ formatMoney(refundForm.refundAmount) }}</span>
        </el-form-item>
        <el-form-item label="退餐原因" prop="refundReason">
          <el-input v-model="refundForm.refundReason" type="textarea" :rows="3" placeholder="请输入退餐原因" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button :loading="refundLoading" type="primary" @click="confirmRefund">确认退餐</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as orderApi from '@/api/customer/order'
import * as dictDetailApi from '@/api/system/dictDetail'
import { refundMeal } from '@/api/mealRefund'
import { createOrderDefaultForm } from '@/components/Order/OrderForm.vue'
import CRUD, { presenter, header, form, crud } from '@crud/crud'
import rrOperation from '@crud/RR.operation'
import crudOperation from '@crud/CRUD.operation'
import OrderForm from '@/components/Order/OrderForm.vue'
import { parseTime } from '@/utils/index'
import { getToken } from '@/utils/auth'
import { mapGetters } from 'vuex'

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
  name: 'CustomerOrder',
  components: { crudOperation, rrOperation, OrderForm },
  mixins: [presenter(), header(), form(createOrderDefaultForm()), crud()],
  cruds() {
    return CRUD({ title: '订单', url: '/api/customer/order', idField: 'id', sort: 'id,desc', crudMethod: { ...orderApi }, query: { orderCode: '', customerCode: '', customerName: '', status: null, customerSource: null, scheduleDate: null }})
  },
  data() {
    return {
      permission: {
        add: ['admin', 'customerOrder:add'],
        edit: ['admin', 'customerOrder:edit'],
        del: ['admin', 'customerOrder:del']
      },
      submitLoading: false,
      refundLoading: false,
      refundDialogVisible: false,
      refundForm: {
        orderId: null,
        orderCode: '',
        customerName: '',
        refundAmount: 0,
        refundReason: ''
      },
      refundRules: {
        refundReason: [{ required: true, message: '请输入退餐原因', trigger: 'blur' }]
      },
      customerSourceOptions: [],
      editRequestId: 0,
      menuDialogVisible: false,
      menuDialogRow: null,
      activeInlineKey: null,
      inlineDrafts: {},
      allergyInputDrafts: {},
      savingRows: {},
      uploadingRows: {},
      rules: {
        customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
        totalAmount: [{
          validator: (rule, value, callback) => {
            if (this.canEditAmount && (value === null || value === undefined || value === '')) {
              callback(new Error('请输入总金额'))
              return
            }
            callback()
          },
          trigger: 'blur'
        }],
        finalAmount: [{
          validator: (rule, value, callback) => {
            if (this.canEditAmount && (value === null || value === undefined || value === '')) {
              callback(new Error('请输入成交金额'))
              return
            }
            callback()
          },
          trigger: 'blur'
        }],
        status: [{ required: true, message: '请选择订单状态', trigger: 'change' }],
        trialOrderId: [{
          validator: (rule, value, callback) => {
            if (this.form.trialConverted && !value) {
              callback(new Error('请选择关联试餐订单'))
              return
            }
            callback()
          },
          trigger: 'change'
        }]
      }
    }
  },
  computed: {
    ...mapGetters(['baseApi', 'imagesUploadApi', 'roles']),
    uploadHeaders() {
      const token = getToken()
      return token ? { Authorization: token } : {}
    },
    canViewAmount() {
      return this.roles.includes('admin') || this.roles.includes('customerOrder:amount:view')
    },
    canEditAmount() {
      return this.roles.includes('admin') || this.roles.includes('customerOrder:amount:edit')
    },
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
      return this.crud.status.add === CRUD.STATUS.PREPARED ? '新增订单' : '编辑订单'
    }
  },
  created() {
    this.loadCustomerSourceDict()
  },
  methods: {
    loadCustomerSourceDict() {
      dictDetailApi.get('customer_source').then(res => {
        this.customerSourceOptions = (res.content || res.data || res || []).map(item => ({
          value: item.value,
          label: item.label
        }))
      }).catch(() => {})
    },
    getSourceLabel(value) {
      if (!value) return '-'
      const item = this.customerSourceOptions.find(o => o.value === value)
      return item ? item.label : value
    },
    /**
     * 获取自定义菜单图片完整访问地址
     */
    getCustomMenuImageUrl(path) {
      if (!path) return ''
      if (path.startsWith('http://') || path.startsWith('https://')) return path
      return this.baseApi + path
    },
    isInlineEditable(row) {
      return (this.roles.includes('admin') || this.roles.includes('customerOrder:edit')) &&
        (row.status === 1 || row.status === 4)
    },
    isInlineBusy(row) {
      return Boolean(this.savingRows[row.id] || this.uploadingRows[row.id])
    },
    /** 判断订单行的 field 是否为当前编辑字段，返回布尔值。 */
    isInlineEditing(row, field) {
      return this.isInlineEditable(row) && this.activeInlineKey === this.inlineDraftKey(row, field)
    },
    /** 根据订单行和字段返回输入框的唯一引用名，用于点击数值后聚焦。 */
    inlineInputRef(row, field) {
      return `inline-input-${row.id}-${field}`
    },
    /** 点击订单行的指定字段值时，只打开该字段的编辑控件。 */
    beginInlineEdit(row, field) {
      if (!this.isInlineEditable(row) || this.isInlineBusy(row)) return
      this.activeInlineKey = this.inlineDraftKey(row, field)
      this.$nextTick(() => {
        let input = this.$refs[this.inlineInputRef(row, field)]
        // 地址列的 ref 位于 v-for 内，Vue 会收集为数组，取未被销毁的实例
        if (Array.isArray(input)) {
          input = input.find(item => !item._isDestroyed) || input[0]
        }
        if (input && this.activeInlineKey === this.inlineDraftKey(row, field)) {
          input.focus()
          const nativeInput = input.$el && input.$el.querySelector('input')
          if (nativeInput) nativeInput.select()
        }
      })
    },
    inlineDraftKey(row, field) {
      return `${row.id}:${field}`
    },
    /** 根据地址槽位返回行内编辑字段键。 */
    addressInlineField(address) {
      return `addressDetail:${address.addressType}`
    },
    /** 返回订单行中指定字段的当前值；地址从对应槽位读取。 */
    getInlineCurrentValue(row, field) {
      if (field.indexOf('addressDetail:') === 0) {
        const addressType = field.substring('addressDetail:'.length)
        const address = (row.addresses || []).find(item => item.addressType === addressType)
        return address ? address.detail : null
      }
      return row[field]
    },
    clearInlineDraftsForRow(row) {
      const prefix = `${row.id}:`
      Object.keys(this.inlineDrafts).forEach(key => {
        if (key.indexOf(prefix) === 0) {
          this.$delete(this.inlineDrafts, key)
        }
      })
    },
    getInlineDraft(row, field) {
      const key = this.inlineDraftKey(row, field)
      if (Object.prototype.hasOwnProperty.call(this.inlineDrafts, key)) {
        return this.inlineDrafts[key]
      }
      const value = this.getInlineCurrentValue(row, field)
      if (field === 'customerCode' || field === 'specialRequirements' || field === 'phone' || field.indexOf('addressDetail:') === 0) {
        return value || ''
      }
      return value === null || value === undefined ? 0 : value
    },
    setInlineDraft(row, field, value) {
      this.$set(this.inlineDrafts, this.inlineDraftKey(row, field), value)
    },
    /** 取消订单行指定字段的草稿；event 存在时同时让输入框失焦。 */
    cancelInlineDraft(row, field, event) {
      const key = this.inlineDraftKey(row, field)
      this.$delete(this.inlineDrafts, key)
      if (field === 'allergyTags') this.$delete(this.allergyInputDrafts, row.id)
      if (this.activeInlineKey === key) this.activeInlineKey = null
      if (event && event.target && event.target.blur) {
        event.target.blur()
      }
    },
    /** 校验并提交订单行指定字段的草稿；失焦或回车时调用，提交失败时恢复展示值。 */
    async submitInlineDraft(row, field) {
      const key = this.inlineDraftKey(row, field)
      if (this.activeInlineKey !== key) return
      this.activeInlineKey = null
      if (this.isInlineBusy(row)) {
        this.$delete(this.inlineDrafts, key)
        return
      }
      const draft = this.getInlineDraft(row, field)
      let value = draft
      if (field === 'breakfastCount' || field === 'lunchDinnerCount' ||
        field === 'mainDishCount' || field === 'sideDishCount' || field === 'vegCount') {
        if (draft === '' || draft === null || draft === undefined || !Number.isInteger(Number(draft)) || Number(draft) < 0) {
          this.$message.warning('餐数必须是大于或等于 0 的整数')
          this.$delete(this.inlineDrafts, key)
          return
        }
        value = Number(draft)
      } else if (field === 'specialRequirements') {
        value = String(draft || '').trim() || null
      } else if (field === 'phone') {
        value = String(draft || '').trim()
        if (!/^1[3-9]\d{9}$/.test(value)) {
          this.$message.warning('手机号格式不正确')
          this.$delete(this.inlineDrafts, key)
          return
        }
      } else if (field.indexOf('addressDetail:') === 0) {
        value = String(draft || '').trim()
        if (!value || value.length > 200) {
          this.$message.warning('地址不能为空且不能超过 200 个字符')
          this.$delete(this.inlineDrafts, key)
          return
        }
      } else if (field === 'customerCode' && !String(draft || '').trim()) {
        this.$message.warning('客户编号不能为空')
        this.$delete(this.inlineDrafts, key)
        return
      }
      const saved = await this.saveInlineValue(row, field, value, this.getInlineCurrentValue(row, field))
      if (saved) {
        this.$delete(this.inlineDrafts, key)
      }
    },
    /** 选择订单行字段的新值后即时提交，返回保存结果 Promise。 */
    saveInlineSelection(row, field, value) {
      if (this.activeInlineKey === this.inlineDraftKey(row, field)) this.activeInlineKey = null
      return this.saveInlineValue(row, field, value, row[field])
    },
    /** 提交订单行单字段的新值和旧值 expectedValue，刷新列表并返回是否保存成功。 */
    async saveInlineValue(row, field, value, expectedValue) {
      if (!this.isInlineEditable(row) || this.savingRows[row.id]) return false
      const normalizedValue = value === undefined ? null : value
      const normalizedExpected = expectedValue === undefined ? null : expectedValue
      if (JSON.stringify(normalizedValue) === JSON.stringify(normalizedExpected)) {
        this.$delete(this.inlineDrafts, this.inlineDraftKey(row, field))
        return true
      }

      this.$set(this.savingRows, row.id, true)
      if (this.activeInlineKey === this.inlineDraftKey(row, field)) this.activeInlineKey = null
      this.clearInlineDraftsForRow(row)
      let saved = false
      try {
        await orderApi.updateInline(row.id, {
          field,
          value: normalizedValue,
          expectedValue: normalizedExpected
        })
        saved = true
        this.$delete(this.inlineDrafts, this.inlineDraftKey(row, field))
        this.$message.success('保存成功')
      } catch (error) {
        this.$delete(this.inlineDrafts, this.inlineDraftKey(row, field))
        if (error && error.response && error.response.status === 409) {
          this.$message.warning('数据已被其他操作修改，已刷新最新值')
        } else {
          this.$message.error((error && error.message) || '保存失败，已恢复最新数据')
        }
      }
      const refreshed = await this.refreshInlineRows()
      if (!refreshed) {
        this.$message.warning('当前页刷新失败，请手动刷新确认最新值')
      }
      this.$delete(this.savingRows, row.id)
      return saved
    },
    async refreshInlineRows() {
      const table = this.$refs.table
      const bodyScrollLeft = table && table.bodyWrapper ? table.bodyWrapper.scrollLeft : 0
      const headerScrollLeft = table && table.headerWrapper ? table.headerWrapper.scrollLeft : bodyScrollLeft
      let refreshed = true
      try {
        const refreshPromise = this.crud.refresh()
        if (refreshPromise) {
          await refreshPromise
        }
        await new Promise(resolve => this.$nextTick(resolve))
      } catch (error) {
        refreshed = false
      } finally {
        const refreshedTable = this.$refs.table
        if (refreshedTable && refreshedTable.bodyWrapper) {
          refreshedTable.bodyWrapper.scrollLeft = bodyScrollLeft
        }
        if (refreshedTable && refreshedTable.headerWrapper) {
          refreshedTable.headerWrapper.scrollLeft = headerScrollLeft
        }
      }
      return refreshed
    },
    getAllergyDraft(row) {
      return this.allergyInputDrafts[row.id] || ''
    },
    setAllergyDraft(row, value) {
      this.$set(this.allergyInputDrafts, row.id, value)
    },
    async addAllergyTag(row) {
      if (this.isInlineBusy(row)) return
      const tag = String(this.getAllergyDraft(row) || '').trim()
      if (!tag) return
      const currentTags = row.allergyTags || []
      if (currentTags.includes(tag)) {
        this.$delete(this.allergyInputDrafts, row.id)
        return
      }
      const saved = await this.saveInlineValue(row, 'allergyTags', currentTags.concat(tag), currentTags)
      if (saved) {
        this.$delete(this.allergyInputDrafts, row.id)
      }
    },
    removeAllergyTag(row, tag) {
      if (this.isInlineBusy(row)) return
      const currentTags = row.allergyTags || []
      const nextTags = currentTags.filter(item => item !== tag)
      return this.saveInlineValue(row, 'allergyTags', nextTags, currentTags)
    },
    /** 点击有菜单图片的订单行时打开预览；可编辑行也允许打开上传弹窗。 */
    openCustomMenuDialog(row) {
      if ((!row.customMenuImage && !this.isInlineEditable(row)) || this.isInlineBusy(row)) return
      this.menuDialogRow = row
      this.menuDialogVisible = true
    },
    /** 删除当前弹窗订单的菜单图片引用，刷新列表后关闭弹窗。 */
    async removeCustomMenuImage() {
      const row = this.menuDialogRow
      if (!row || !row.customMenuImage || !this.isInlineEditable(row) || this.isInlineBusy(row)) return
      await this.saveInlineValue(row, 'customMenuImage', null, row.customMenuImage)
      this.menuDialogVisible = false
    },
    getCustomMenuBeforeUpload(row) {
      return file => {
        if (this.isInlineBusy(row)) return false
        if (!file.type || !file.type.startsWith('image/')) {
          this.$message.error('只能上传图片文件')
          return false
        }
        if (file.size / 1024 / 1024 >= 5) {
          this.$message.error('图片大小不能超过 5MB')
          return false
        }
        this.$set(this.uploadingRows, row.id, true)
        return true
      }
    },
    getCustomMenuUploadSuccess(row) {
      return async response => {
        const data = response || {}
        if (!data.type || !data.realName) {
          this.$message.error('图片已上传，但未返回有效文件路径')
          this.$delete(this.uploadingRows, row.id)
          return
        }
        const path = `/file/${data.type}/${data.realName}`
        try {
          await this.saveInlineValue(row, 'customMenuImage', path, row.customMenuImage)
        } finally {
          this.$delete(this.uploadingRows, row.id)
          if (this.menuDialogRow && this.menuDialogRow.id === row.id) this.menuDialogVisible = false
        }
      }
    },
    getCustomMenuUploadError(row) {
      return () => {
        this.$delete(this.uploadingRows, row.id)
        this.$message.error('菜单图片上传失败')
      }
    },
    [CRUD.HOOK.beforeToCU]() {
      const currentForm = { ...this.form }
      Object.assign(this.form, createOrderDefaultForm(), currentForm)
      return true
    },
    [CRUD.HOOK.beforeToAdd]() {
      // 重置表单
      Object.assign(this.form, createOrderDefaultForm())
      return true
    },
    [CRUD.HOOK.beforeSubmit]() {
      // 计算余额和剩余餐数
      this.onCalcChange()
      return true
    },
    handleDialogClose(done) {
      this.cancelDialog()
      done && done()
    },
    cancelDialog() {
      this.crud.cancelCU()
    },
    async handleEdit(row) {
      const requestId = this.editRequestId + 1
      this.editRequestId = requestId
      try {
        const res = await orderApi.getOrder(row.id)
        if (requestId !== this.editRequestId) {
          return
        }
        const detail = res.data || res
        // 订单详情接口不返回客户编号，这里沿用列表行数据，确保编辑态客户回显使用客户编号。
        detail.customerCode = row.customerCode || detail.customerCode
        this.crud.toEdit(detail)
      } catch (e) {
        if (requestId !== this.editRequestId) {
          return
        }
        this.$message.error('获取订单详情失败: ' + (e.message || '未知错误'))
      }
    },
    async submitForm() {
      const valid = await this.$refs.orderFormRef.validate().catch(() => false)
      if (!valid) return
      if (this.form.trialConverted && !this.form.trialOrderId) {
        this.$message.warning('请选择关联试餐订单')
        return
      }

      // 前端校验换菜规则：原菜不能重复
      const rules = this.form.replaceRules
      if (rules && rules.length > 0) {
        const sourceIds = rules.map(r => r.sourceDishId).filter(Boolean)
        if (new Set(sourceIds).size !== sourceIds.length) {
          this.$message.warning('同一订单不能重复配置同一个原菜')
          return
        }
        for (const rule of rules) {
          if (!rule.sourceDishId) {
            this.$message.warning('换菜规则中的原菜不能为空')
            return
          }
          if (!rule.targetDishId) {
            this.$message.warning('换菜规则中的目标菜不能为空')
            return
          }
          if (rule.sourceDishId === rule.targetDishId) {
            this.$message.warning('原菜和目标菜不能相同')
            return
          }
        }
      }

      const payload = {
        ...this.form,
        replaceRules: cleanReplaceRules(this.form.replaceRules)
      }
      if (!this.canEditAmount) {
        delete payload.depositAmount
        delete payload.totalAmount
        delete payload.finalAmount
        delete payload.breakfastPrice
        delete payload.lunchDinnerPrice
        delete payload.verifiedAmount
      }

      // 先校验订单冲突（提交前校验）
      try {
        await orderApi.validateOrder(payload)
      } catch (e) {
        this.$message.warning(e.message || '订单校验失败')
        return // 校验失败则阻止提交
      }

      try {
        this.submitLoading = true
        if (this.form.id) {
          await orderApi.edit(payload)
          this.$message.success('编辑成功')
        } else {
          await orderApi.add(payload)
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
    onCustomerChange(customerId, customer) {
      // 客户变更事件处理（如有需要可扩展）
    },
    onCalcChange() {
      // 余额计算
      const finalAmt = this.form.finalAmount || 0
      const verifiedAmt = this.form.verifiedAmount || 0
      this.form.mealBalance = Math.max(0, finalAmt - verifiedAmt)
      // 剩余餐数计算
      const breakfast = this.form.breakfastCount || 0
      const lunchDinner = this.form.lunchDinnerCount || 0
      const total = breakfast + lunchDinner
      const verified = this.form.verifiedCount || 0
      this.form.remainingCount = Math.max(0, total - verified)
    },
    statusText(status) {
      if (status === 0) return '已取消'
      if (status === 1) return '进行中'
      if (status === 2) return '已完成'
      if (status === 3) return '已退餐'
      if (status === 4) return '暂停'
      return '未知'
    },
    statusTagType(status) {
      if (status === 0) return 'danger'
      if (status === 1) return 'success'
      if (status === 2) return 'info'
      if (status === 3) return 'warning'
      if (status === 4) return 'warning'
      return 'info'
    },
    mealTypeText(mealType, status) {
      if (!mealType) return '待通知'
      if (mealType === 'ALL') return '-'
      const map = { LUNCH: '午餐', DINNER: '晚餐', LUNCH_DINNER: '午+晚' }
      return map[mealType] || mealType
    },
    scheduleModeText(scheduleMode) {
      const map = {
        SCHEDULE: '指定日期',
        DAILY: '每天送',
        WEEKEND: '周末送',
        WEEKDAY: '工作日'
      }
      return map[scheduleMode] || '-'
    },
    startMealTypeText(startMealType) {
      const map = {
        BREAKFAST: '早餐起',
        LUNCH: '午餐起',
        DINNER: '晚餐起'
      }
      return map[startMealType] || '早餐起'
    },
    formatOrderPeriod(row) {
      if (!row.startDate) return '-'
      const startDate = this.formatDate(row.startDate)
      if (!row.mealType) return `${startDate}（待通知）起`
      return `${startDate}（${this.startMealTypeText(row.startMealType)}）起`
    },
    checkboxT() {
      return true
    },
    formatMoney(val) {
      const amount = Number(val)
      if (Number.isNaN(amount)) {
        return '0.00'
      }
      return amount.toFixed(2)
    },
    packageSpecText(row) {
      const meat = (row.mainDishCount || 0) + (row.sideDishCount || 0)
      const veg = row.vegCount || 0
      const soup = row.soupCount || 0
      return soup > 0 ? `${meat}荤${veg}素-${soup}汤` : `${meat}荤${veg}素`
    },
    formatDate(val) {
      if (!val) return '-'
      return parseTime(val, '{y}-{m}-{d}')
    },
    openRefundDialog(row) {
      this.refundForm = {
        orderId: row.id,
        orderCode: row.orderCode,
        customerName: row.customerName,
        refundAmount: row.mealBalance || 0,
        refundReason: ''
      }
      this.refundDialogVisible = true
      this.$nextTick(() => {
        this.$refs.refundFormRef && this.$refs.refundFormRef.clearValidate()
      })
    },
    confirmRefund() {
      this.$refs.refundFormRef.validate(async valid => {
        if (!valid) return
        this.refundLoading = true
        try {
          await refundMeal({
            orderId: this.refundForm.orderId,
            refundReason: this.refundForm.refundReason
          })
          this.$message.success('退餐成功')
          this.refundDialogVisible = false
          this.crud.refresh()
        } catch (e) {
          this.$message.error(e.message || '退餐失败')
        } finally {
          this.refundLoading = false
        }
      })
    }
  }
}
</script>

<style scoped>
.head-container {
  padding: 10px;
  margin-bottom: 10px;
}
.head-container .filter-item {
  margin-right: 10px;
}
.column-help {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.column-help .el-icon-question {
  color: #909399;
  cursor: help;
  font-size: 13px;
}
.inline-spec-field {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  white-space: nowrap;
}
.inline-spec-field .el-input {
  width: 42px;
}
.inline-value.is-editable {
  cursor: pointer;
  border-bottom: 1px dashed transparent;
}
.inline-value.is-editable:hover {
  color: #409eff;
  border-bottom-color: #409eff;
}
.inline-spec-field .inline-value {
  min-width: 12px;
  text-align: center;
}
.inline-address-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 2px;
}
.inline-address-row .el-input {
  flex: 1;
  min-width: 140px;
}
.inline-address-row .el-tag {
  height: auto;
  white-space: normal;
}
.inline-address-row .is-editable {
  cursor: pointer;
}
.inline-address-row .is-editable:hover {
  border-color: #409eff;
}
.inline-menu-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  cursor: pointer;
}
.inline-menu-cell:hover .inline-value {
  color: #409eff;
}
.menu-dialog-body {
  text-align: center;
}
.menu-dialog-image {
  width: 100%;
  height: 240px;
  cursor: pointer;
}
.menu-dialog-empty {
  padding: 70px 0;
  color: #909399;
  border: 1px dashed #dcdfe6;
}
.menu-dialog-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}
.menu-dialog-hint {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
}
.inline-allergy-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 3px;
}
.inline-allergy-input {
  display: flex;
  align-items: center;
  width: 100%;
}
</style>
