<template>
  <section class="page-card">
    <div class="page-header">
      <div>
        <h1>提交状态</h1>
        <p>支持按姓名、部门、提交状态和负责人筛选。</p>
      </div>
      <BrowserDownloadHint>
        <el-button round @click="emit('download')">下载 Excel</el-button>
      </BrowserDownloadHint>
    </div>

    <div class="toolbar">
      <el-input v-model="filters.keyword" clearable placeholder="搜索姓名、部门、职务" />
      <el-select v-model="filters.status" clearable placeholder="提交状态">
        <el-option label="已提交" value="已提交" />
        <el-option label="未提交" value="未提交" />
      </el-select>
      <el-select v-model="filters.leader" clearable placeholder="负责人">
        <el-option label="是" value="是" />
        <el-option label="否" value="否" />
      </el-select>
    </div>

    <el-table :data="filteredRows" class="soft-table" height="560" row-key="userid">
      <el-table-column prop="提交状态" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row['提交状态'] === '已提交' ? 'success' : 'danger'" effect="light">
            {{ row['提交状态'] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="姓名" label="姓名" width="130" />
      <el-table-column prop="部门" label="部门" min-width="190" show-overflow-tooltip />
      <el-table-column prop="职务" label="职务" min-width="180" show-overflow-tooltip />
      <el-table-column prop="是否负责人候选" label="负责人" width="100" />
      <el-table-column prop="提交时间" label="提交时间" min-width="170" />
    </el-table>
  </section>
</template>

<script setup>
import { computed, reactive } from 'vue'
import BrowserDownloadHint from '../../components/BrowserDownloadHint.vue'

const props = defineProps({ rows: { type: Array, default: () => [] } })
const emit = defineEmits(['download'])
const filters = reactive({ keyword: '', status: '', leader: '' })

const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return props.rows
    .map((row, index) => ({ row, index }))
    .filter(item => {
      const text = `${item.row['姓名'] || ''} ${item.row['部门'] || ''} ${item.row['职务'] || ''}`.toLowerCase()
      return (!keyword || text.includes(keyword))
        && (!filters.status || item.row['提交状态'] === filters.status)
        && (!filters.leader || item.row['是否负责人候选'] === filters.leader)
    })
    .sort((left, right) => {
      const leftMissing = left.row['提交状态'] === '未提交'
      const rightMissing = right.row['提交状态'] === '未提交'
      if (leftMissing !== rightMissing) return leftMissing ? -1 : 1
      return left.index - right.index
    })
    .map(item => item.row)
})
</script>
