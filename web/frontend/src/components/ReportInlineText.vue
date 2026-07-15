<template>
  <template v-for="(part, partIndex) in renderedParts" :key="partIndex">
    <a v-if="part.type === 'link'" :href="part.href" target="_blank" rel="noreferrer">{{ part.text }}</a>
    <code v-else-if="part.type === 'code'">{{ part.text }}</code>
    <strong v-else-if="part.type === 'strong'">
      <template v-for="(token, tokenIndex) in part.tokens" :key="tokenIndex">
        <PersonReportLink
          v-if="token.type === 'person'"
          :name="token.text"
          :candidates="token.candidates"
          @select="emit('person-select', $event)"
        />
        <span v-else>{{ token.text }}</span>
      </template>
    </strong>
    <template v-else>
      <template v-for="(token, tokenIndex) in part.tokens" :key="tokenIndex">
        <PersonReportLink
          v-if="token.type === 'person'"
          :name="token.text"
          :candidates="token.candidates"
          @select="emit('person-select', $event)"
        />
        <span v-else>{{ token.text }}</span>
      </template>
    </template>
  </template>
</template>

<script setup>
import { computed } from 'vue'
import PersonReportLink from './PersonReportLink.vue'
import { matchPeople } from './markdown/personMatcher.js'

const props = defineProps({
  parts: { type: Array, required: true },
  people: { type: Array, default: () => [] }
})

const emit = defineEmits(['person-select'])

const renderedParts = computed(() => props.parts.map(part => {
  if (part.type === 'link' || part.type === 'code') return part
  return { ...part, tokens: matchPeople(part.text, props.people) }
}))
</script>
