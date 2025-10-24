<#include "/shared/accordion.ftl">

<#macro sidebar id>
  <aside class="dv-sidebar">
    <div class="dv-sidebar-header">
      <h1 class="dv-bold">Dynamic Visualizations</h1>
    </div>

    <div class="dv-sidebar-body">
      <a 
        class="dv-btn dv-menu-link"
        href="/"
      >
        <i class="bi bi-list"></i>
        <span>Menu</span>
      </a>

      <div class="dv-menu-title">Pipeline Editor</div>
      <label class="dv-label">
        <span>Identifier:</span>
        <input id="identifier-input" type="text" class="dv-text-input" value="${id}" />
      </label>

      <label class="dv-label">Existing Generators:</label>
      <div id="added-generators" class="dv-generators-container">
        <template id="added-generator-template">
          <div class="dv-generator">
            <div class="dv-generator-name">
              <span class="font-monospace"></span>
              <span></span>
            </div>
            <div class="d-flex">
              <button class="dv-btn" type="button" title="Edit">
                <i class="bi bi-pencil"></i>
              </button>
              <button class="dv-btn" type="button" title="Remove">
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
          </div>
        </template>
      </div>

      <@accordion icon="bi bi-database" title="Generators">
        <p>Generators are data representations for the charts. They keep record of all the data that is relevant for the charts.</p>
        <div id="available-generators" class="dv-generators-container">
          <template id="available-generator-template">
            <div class="dv-generator">
              <div class="dv-generator-name-tooltip">
                <span class="font-monospace"></span>
                <span></span>
                <div class="dv-generator-tooltip"><strong>Tooltip</strong><br>Explanation text goes here.</div>
              </div>
              <button class="dv-btn" type="button">
                <i class="bi bi-plus-lg"></i>
              </button>
            </div>
          </template>
        </div>
      </@accordion>

      <@accordion icon="bi bi-grid" title="Widgets">
        <p>Add new widgets by dragging them into the grid area to the right.</p>
        <div class="dv-available-widgets-container">
          <template id="available-widget-template">
            <div class="dv-available-widget">
              <div class="dv-available-widget-draggable">
                <i></i>
              </div>
              <span class="dv-available-widget-title"></span>
            </div>
          </template>
        </div>
      </@accordion>
    </div>

    <div class="dv-sidebar-footer">
      <div class="dv-btn-group">
        <button id="discard-button" type="button" class="dv-btn-outline">
          <i class="bi bi-x-lg"></i>
          <span>Discard</span>
        </button>
        <button id="save-button" type="button" class="dv-btn-primary">
          <i class="bi-floppy"></i>
          <span>Save</span>
        </button>
      </div>
    </div>
  </aside>
</#macro>
