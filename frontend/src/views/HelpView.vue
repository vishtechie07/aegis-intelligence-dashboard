<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { HELP_SECTIONS } from '@/content/helpSections'

const router = useRouter()
const openId = ref<string | null>(HELP_SECTIONS[0]?.id ?? null)

function toggle(id: string) {
  openId.value = openId.value === id ? null : id
}

function back() {
  void router.push('/')
}
</script>

<template>
  <div class="min-h-screen bg-gray-950 text-gray-100">
    <header class="sticky top-0 z-10 border-b border-gray-800 bg-gray-950/90 px-4 py-3 backdrop-blur">
      <div class="mx-auto flex max-w-3xl items-center gap-4">
        <button type="button" class="text-sm text-gray-400 hover:text-white" @click="back">← Dashboard</button>
        <div>
          <h1 class="text-xl font-bold text-white">Guide</h1>
          <p class="text-xs text-gray-500">How Aegis works on this deployment</p>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-3xl px-4 py-6">
      <p class="mb-6 text-sm text-gray-400">
        Quick orientation for new users and reviewers. Expand a section below.
      </p>

      <div class="space-y-2">
        <section
          v-for="section in HELP_SECTIONS"
          :id="section.id"
          :key="section.id"
          class="overflow-hidden rounded-lg ring-1 ring-gray-800 bg-gray-900/40"
        >
          <button
            type="button"
            class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left text-sm font-medium text-gray-100 hover:bg-gray-800/50"
            :aria-expanded="openId === section.id"
            @click="toggle(section.id)"
          >
            <span>{{ section.title }}</span>
            <span class="text-gray-500" aria-hidden="true">{{ openId === section.id ? '−' : '+' }}</span>
          </button>

          <div v-show="openId === section.id" class="border-t border-gray-800 px-4 py-3 text-sm text-gray-300">
            <p v-for="(p, i) in section.paragraphs" :key="`p-${i}`" class="mb-2 last:mb-0">{{ p }}</p>
            <ul v-if="section.bullets?.length" class="mt-2 list-disc space-y-1 pl-5 text-gray-400">
              <li v-for="(b, i) in section.bullets" :key="i">{{ b }}</li>
            </ul>
            <figure v-if="section.image" class="mt-4">
              <img
                :src="section.image.src"
                :alt="section.image.alt"
                class="w-full rounded-md ring-1 ring-gray-700"
                loading="lazy"
              />
              <figcaption v-if="section.image.caption" class="mt-2 text-xs text-gray-500">
                {{ section.image.caption }}
              </figcaption>
            </figure>
            <a
              v-if="section.link"
              :href="section.link.href"
              class="mt-3 inline-block text-sm text-cyan-400 hover:text-cyan-300"
              :target="section.link.external ? '_blank' : undefined"
              :rel="section.link.external ? 'noopener noreferrer' : undefined"
            >
              {{ section.link.label }} →
            </a>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>
