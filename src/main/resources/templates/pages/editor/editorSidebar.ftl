<#macro sidebar id>
  <aside class="dv-sidebar">
    <a class="dv-sidebar-header" href="/">
      <img src="/img/logo.png" alt="Logo">
      <span class="dv-bold">UDAV</span>
    </a>

    <div class="dv-sidebar-body">
      <div class="dv-editor-badge">Pipeline Editor</div>

      <label class="dv-label w-100 my-4">
        <span>Identifier:</span>
        <input id="identifier-input" type="text" class="dv-text-input" value="${id}" />
      </label>

      <ul class="nav nav-tabs nav-justified" role="tablist">
        <li class="nav-item" role="presentation">
          <button 
            class="nav-tab active" 
            id="generators-tab" 
            data-bs-toggle="tab" 
            data-bs-target="#generators-tab-pane" 
            type="button" 
            role="tab" 
          >
            <div class="dv-accordion-title">
              <i class="bi bi-database"></i>
              <span>Generators</span>
            </div>
          </button>
        </li>
        <li class="nav-item" role="presentation">
          <button 
            class="nav-tab" 
            id="widgets-tab" 
            data-bs-toggle="tab" 
            data-bs-target="#widgets-tab-pane" 
            type="button" 
            role="tab"
          >
            <div class="dv-accordion-title">
              <i class="bi bi-grid"></i>
              <span>Widgets</span>
            </div>
          </button>
        </li>
      </ul>
      <div class="tab-content">
        <div class="tab-pane fade show active" id="generators-tab-pane" role="tabpanel" tabindex="0">
          <p>
            Generators are data representations for the charts. 
            They keep record of all the data that is relevant for the charts.
          </p>
          
          <div class="dv-sources-container">
            <button class="dv-add-source-button">
              <i class="bi bi-plus-lg"></i>
              <span>New source</span>
            </button>
          </div>
        </div>
        <div class="tab-pane fade" id="widgets-tab-pane" role="tabpanel" tabindex="0">
          <p>
            Add new widgets by dragging them into the grid area to the right.<br>
            <strong>Note:</strong> Dynamic widgets in the editor will display sample data for preview purposes.
          </p>
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
        </div>
      </div>

      <template id="source-card-template">
        <div class="dv-source-card">
          <div class="dv-source-card-header">
            <div>
              <button class="dv-btn" type="button" title="New generator" data-bs-toggle="dropdown">
                <i class="bi bi-plus-lg"></i>
              </button>
              <div class="dropdown-menu">
                <div class="dv-dropdown-menu"></div>
              </div>
              <span class="dv-bold">Source</span>
            </div>
            <div>
              <button class="dv-btn" type="button" title="Edit">
                <i class="bi bi-pencil"></i>
              </button>
              <button class="dv-btn" type="button" title="Remove">
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
          </div>
          <div class="dv-source-card-body"></div>
        </div>
      </template>

      <template id="generator-card-template">
        <div class="dv-generator-card">
          <div class="dv-generator-card-title">
            <div>
              <div class="dv-generator-card-token"></div>
            </div>
            <div class="overflow-hidden">
              <div class="dv-generator-card-type"></div>
              <div class="dv-generator-card-name"></div>
            </div>
          </div>
          <div class="dv-generator-card-buttons">
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
