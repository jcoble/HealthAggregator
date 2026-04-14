<script lang="ts">
  let { field, errors, touched }: {
    field: string;
    errors: Record<string, string>;
    touched?: Record<string, boolean>;
  } = $props();
</script>

{#if errors[field] && (!touched || touched[field])}
  <p class="text-destructive text-xs mt-1" data-testid="field-error-{field}">{errors[field]}</p>
{/if}
