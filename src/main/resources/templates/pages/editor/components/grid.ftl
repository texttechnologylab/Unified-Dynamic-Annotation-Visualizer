<#macro grid>
  <div class="grid-stack"></div>

  <template id="default-static-template">
    <div class="grid-stack-item-content">
      <button
        class="dv-btn-autohide"
        type="button"
        title="Edit"
      >
        <i class="bi bi-pencil"></i>
      </button>
      <span></span>
      <button
        class="dv-btn-autohide"
        type="button"
        title="Remove"
      >
        <i class="bi bi-x-lg"></i>
      </button>
    </div>
  </template>

  <template id="default-chart-template">
    <div class="grid-stack-item-content dv-chart">
      <div class="dv-toolbar">
        <button
          class="dv-btn dv-btn-toolbar"
          type="button"
          title="Edit"
        >
          <i class="bi bi-pencil"></i>
        </button>

        <span class="dv-toolbar-title"></span>

        <button
          class="dv-btn dv-btn-toolbar"
          type="button"
          title="Remove"
        >
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
      <div class="dv-chart-area"></div>
    </div>
  </template>
</#macro>
