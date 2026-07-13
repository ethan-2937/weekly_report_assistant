<template>
  <section class="login-shell login-shell--official">
    <div class="login-orbit login-orbit--one"></div>
    <div class="login-orbit login-orbit--two"></div>

    <div class="login-stage">
      <aside class="login-poster">
        <div class="login-poster__brand">
          <span class="brand-mark brand-logo-wrap">
            <img :src="youzhiLogo" alt="优智科技 Youzhi" />
          </span>
          <span>
            <strong>优智周报系统</strong>
            <small>周报汇总与评价平台</small>
          </span>
        </div>
        <div class="login-poster__copy">
          <span class="login-eyebrow">MANAGEMENT CONSOLE</span>
          <h1>工作周报汇总与评价系统</h1>
          <p>统一登录、权限分级、钉钉身份绑定，提供安全、规范的周报汇总与评价入口。</p>
        </div>
        <div class="login-poster__note">
          <span>周报数据仅向授权账号开放</span>
          <strong>请使用本人账号登录，首次登录后及时修改初始密码。</strong>
        </div>
      </aside>

      <div class="login-card login-card--official">
        <div class="login-card__head">
          <span class="login-eyebrow">SECURE SIGN IN</span>
          <h2>登录周报系统</h2>
          <p>请使用系统账号登录；已绑定钉钉身份的用户可直接走钉钉授权。</p>
        </div>

        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

        <div class="login-form">
          <label>
            <span>用户名</span>
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" @keyup.enter="submit" />
          </label>
          <label>
            <span>密码</span>
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              @keyup.enter="submit"
            />
          </label>
          <el-button type="primary" size="large" round :loading="busy" @click="submit">进入工作台</el-button>
          <el-button class="ding-login-btn" size="large" round :loading="dingtalkBusy" @click="$emit('dingtalk-login')">
            使用钉钉登录
          </el-button>
        </div>

        <div class="login-hint login-hint--official">
          <strong>安全提示</strong>
          <span>正式使用前请及时修改初始密码，并由管理员按需配置账号权限与钉钉身份绑定。</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { reactive } from 'vue'
import youzhiLogo from '../../assets/youzhi-logo-transparent.png'

defineProps({
  error: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  dingtalkBusy: { type: Boolean, default: false }
})

const emit = defineEmits(['login', 'dingtalk-login'])
const form = reactive({ username: 'admin', password: '' })

function submit() {
  emit('login', { username: form.username, password: form.password })
}
</script>
