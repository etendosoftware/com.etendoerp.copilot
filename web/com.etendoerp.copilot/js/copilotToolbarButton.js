(function () {

  function openCopilotWindow(q, assistant_id) {
    if (!q) {
      q = '';
    } else {
      q = encodeURI(q);
    }
    if (!assistant_id) {
      assistant_id = '';
    } else {
      const idRegex = /^[A-Fa-f0-9]{32}$/;
      assistant_id = idRegex.test(assistant_id) ? assistant_id : '';
    }

    var URL = `web/com.etendoerp.copilot.dist/?question=${q}&assistant_id=${assistant_id}`;
    var LIGHT_GRAY_COLOR = "#F2F5F9";
    var WINDOW_WIDTH = 425;
    var WINDOW_HEIGHT = 650;
    var MAXIMIZED_WINDOW_HEIGHT = 650;
    var MAXIMIZED_WINDOW_WIDTH = 425;
    var MINIMIZED_WINDOW_HEIGHT = 55;
    var MINIMIZED_WINDOW_WIDTH = 131;
    var MARGIN_CONTAINER_HORIZONTAL = 1;
    var MARGIN_CONTAINER_VERTICAL = 6;
    var MARGIN_CONTAINER_FULL_SCREEN = 12;
    var MARGIN_CONTAINER_FULL_SCREEN_HORIZONTAL = 13;
    var MARGIN_CONTAINER_FULL_SCREEN_VERTICAL = 18;

    function adjustFullScreenWindowPosition() {
      var header = document.getElementById('chatHeader');
      if (header) {
        header.style.backgroundColor = '#F2F5F9';
      }
      var reactIframe = document.getElementById('react-iframe');
      var reactDoc = reactIframe.contentDocument || reactIframe.contentWindow.document;
      if (reactDoc) {
        var iframeSelector = reactDoc.getElementById('iframe-selector');
        var iframeContainer = reactDoc.getElementById('iframe-container');
        var assistantTitle = reactDoc.getElementById('assistant-title');
        if (assistantTitle) {
          assistantTitle.style.display = 'flex';
        }
        if (iframeContainer && iframeSelector) {
          iframeSelector.classList.add("iframe-selector-full-screen");
          iframeContainer.classList.add("iframe-container-full-screen");
        }
      }
      var imgElement = document.getElementById('maximizeIcon');
      if (imgElement) {
        imgElement.src = "web/images/maximize-2.svg";
      }
      window.copilotWindow.setLeft(MARGIN_CONTAINER_FULL_SCREEN);
      window.copilotWindow.setTop(MARGIN_CONTAINER_FULL_SCREEN);
      window.copilotWindow.setWidth(isc.Page.getWidth() - MARGIN_CONTAINER_FULL_SCREEN_HORIZONTAL);
      window.copilotWindow.setHeight(isc.Page.getHeight() - MARGIN_CONTAINER_FULL_SCREEN_VERTICAL);
    }

    function adjustMinimizeWindowPosition() {
      var widget = document.querySelector('.widgetContainer');
      if (widget) {
        widget.style.setProperty('border', '0px solid #666', 'important');
      }
      var body = document.getElementById('chatBody');
      if (body) {
        body.style.display = 'none';
      }
      var button = document.getElementById('button-minimize');
      if (button) {
        button.style.display = 'flex';
      }
      window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);
      var newLeft = Math.max(0, isc.Page.getWidth() - MINIMIZED_WINDOW_WIDTH - MARGIN_CONTAINER_HORIZONTAL);
      var newTop = Math.max(0, isc.Page.getHeight() - MINIMIZED_WINDOW_HEIGHT - MARGIN_CONTAINER_VERTICAL);
      window.copilotWindow.setLeft(newLeft);
      window.copilotWindow.setTop(newTop);
      window.copilotWindow.setWidth(MINIMIZED_WINDOW_WIDTH);
      window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);
    }

    function adjustMaximizeWindowPosition() {
      var header = document.getElementById('chatHeader');
      if (header) {
        header.style.backgroundColor = '#FFFFFF';
      }
      var widget = document.querySelector('.widgetContainer');
      if (widget) {
        widget.style.setProperty('margin', '0px', 'important');
        widget.style.setProperty('border', '1px solid #666', 'important');
      }

      var reactIframe = document.getElementById('react-iframe');
      var reactDoc = reactIframe.contentDocument || reactIframe.contentWindow.document;
      if (reactDoc) {
        var iframeSelector = reactDoc.getElementById('iframe-selector');
        var iframeContainer = reactDoc.getElementById('iframe-container');
        var assistantTitle = reactDoc.getElementById('assistant-title');
        if (assistantTitle) {
          assistantTitle.style.display = 'none';
        }
        if (iframeContainer && iframeSelector) {
          iframeSelector.classList.remove("iframe-selector-full-screen");
          iframeContainer.classList.remove("iframe-container-full-screen");
        }
      }
      var body = document.getElementById('chatBody');
      if (body) {
        body.style.display = 'flex';
      }
      var imgElement = document.getElementById('maximizeIcon');
      if (imgElement) {
        imgElement.src = "web/images/maximize.svg";
      }
      var button = document.getElementById('button-minimize');
      if (button) {
        button.style.display = 'none';
      }

      window.copilotWindow.setWidth(isc.Page.getWidth());
      window.copilotWindow.setHeight(isc.Page.getHeight());

      window.copilotWindow.setWidth(MAXIMIZED_WINDOW_WIDTH);
      window.copilotWindow.setHeight(MAXIMIZED_WINDOW_HEIGHT);

      var newLeft = Math.max(0, isc.Page.getWidth() - MAXIMIZED_WINDOW_WIDTH - MARGIN_CONTAINER_HORIZONTAL);
      var newTop = Math.max(0, isc.Page.getHeight() - MAXIMIZED_WINDOW_HEIGHT - MARGIN_CONTAINER_VERTICAL);
      window.copilotWindow.setLeft(newLeft);
      window.copilotWindow.setTop(newTop);
    }

    function resizeWindow() {
      if (!window.copilotWindow) {
        return;
      }
      if (window.copilotWindow.height === MAXIMIZED_WINDOW_HEIGHT) {
        adjustMaximizeWindowPosition();
      } else if (window.copilotWindow.height > MAXIMIZED_WINDOW_HEIGHT) {
        adjustFullScreenWindowPosition();
      } else {
        adjustMinimizeWindowPosition();
      }
    }

    window.handleMinimize = function () {
      adjustMinimizeWindowPosition();
    };
    window.handleMaximize = function () {
      adjustMaximizeWindowPosition();
    };
    window.handleFullScreenWindow = function () {
      if (window.copilotWindow.height === MAXIMIZED_WINDOW_HEIGHT) {
        adjustFullScreenWindowPosition();
      } else {
        adjustMaximizeWindowPosition();
      }
    };
    window.closeCopilotWindow = function () {
      if (window.copilotWindow) {
        window.copilotWindow.destroy();
        window.copilotWindow = null;
      }
    };

    if (!window.copilotWindow) {
      window.copilotWindow = isc.Window.create({
        width: WINDOW_WIDTH,
        styleName: 'widgetContainer',
        height: WINDOW_HEIGHT,
        canDragReposition: true,
        headerProperties: {
          height: "0px"
        },
        backgroundColor: LIGHT_GRAY_COLOR,
        items: [
          isc.HTMLPane.create({
            width: "100%",
            height: "100%",
            contents: `
              <div style="width:100%; height:100%; display:flex; flex-direction:column;">
                <style>
                  .icon-button {
                    width: 24px;
                    height: 24px;
                    cursor: pointer;
                    margin-left: 8px;
                  }
                  .close-button-container {
                    padding: 0.3rem;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    border-radius: 0.5rem;
                    cursor: pointer;
                  }
                  .close-button-container:hover {
                    background-color: #EFF1F7;
                  }
                  .copilot-logotype {
                    width: 3rem;
                    height: 3rem;
                  }
                  .container-header {
                    display: flex;
                    justify-content: space-between;
                    padding: 0px 12px;
                    align-items: center;
                    background-color: #FFFFFF;
                    border-bottom: 1px solid #D3D6E1;
                    height: 56px;
                  }
                  .content-header {
                    display: flex;
                    gap: 0.75rem;
                    align-items: center;
                    padding-top: 0.5rem;
                    padding-bottom: 0.5rem;
                    justify-content: center;
                  }
                  .copilot-title {
                    font-size: 1.5rem;
                    font-weight: bold;
                    color: #666666;
                  }
                  .copilot-title-minimize {
                    font-size: 16px;
                    font-weight: bold;
                    color: #FFFFFF;
                  }
                  .normal {
                    display: flex;
                    flex-direction: column;
                  }
                  .action-buttons-container {
                    align-items: center;
                    display: flex;
                  }
                  .copilot-minimize-button {
                    display: none;
                    height: 100%;
                    width: 100%;
                    border-radius: 20px;
                    background-color: #202452;
                    justify-content: center;
                    align-items: center;
                    border-style: none;
                  }
                  .copilot-logotype-minimize {
                    width: 32px;
                    height: 32px;
                    margin-right: 8px;
                  }
                  #chatBody {
                    display: flex;
                    flex-direction: column;
                    height: 100%;
                  }
                </style>

                <button onclick="window.handleMaximize()" id="button-minimize" class="copilot-minimize-button">
                  <img id="copilote-img-min" class="copilot-logotype-minimize" src="web/images/copilot-min.png" alt="Logo Copilot">
                  <span class="copilot-title-minimize">Copilot</span>
                </button>

                <div id="chatBody">
                  <div id="chatHeader" class="container-header">
                    <div class="content-header">
                      <img id="copilote-img-max" class="copilot-logotype" src="web/images/copilot.png" alt="Logo Copilot">
                      <span class="copilot-title">Copilot</span>
                    </div>
                    <div class="action-buttons-container">
                        <img class="icon-button" onclick="window.handleMinimize()" src="web/images/minimize.svg" alt="Minimize">
                        <img id="maximizeIcon" class="icon-button" onclick="window.handleFullScreenWindow()" src="web/images/maximize.svg" alt="Maximize">
                        <img class="icon-button" onclick="window.closeCopilotWindow()" src="web/images/close.svg" alt="Close">
                    </div>
                  </div>

                  <iframe
                    id="react-iframe"
                    style="display: block; width: 100%; flex:1"
                    src="${URL}"
                    title="Copilot Chat"
                    frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                    allowfullscreen>
                  </iframe>
                </div>
              </div>
            `
          })
        ]
      });
    }

    window.copilotWindow.show();
    isc.Page.setEvent("resize", resizeWindow);

    adjustMaximizeWindowPosition();
  }

  // NOTE: The console.logs in this block are for development purposes only and will be removed 
  // before the final release of the epic. They are used to debug and track the flow of data 
  // during the implementation phase.
  var buttonProps = {
    action: function () {
      console.log("Copilot button clicked");
      openCopilotWindow(null, null);

      var callback, orders = [], i,
        view = this.view,
        grid = view.viewGrid,
        selectedRecords = grid.getSelectedRecords();

      for (i = 0; i < selectedRecords.length; i++) {
        orders.push(selectedRecords[i].id);
      }

      console.log("Selected orders:", orders);

      var currentWindowId = this.view.windowId;
      var currentTabId = this.view.tabId;
      var currentTabTitle = this.view.tabTitle;

      console.log("Current window:", currentWindowId);
      console.log("Current tab:", currentTabId);
      console.log("Current tab title:", currentTabTitle);

      var activeWindowInfo = {
        windowId: currentWindowId,
        tabId: currentTabId,
        title: currentTabTitle
      };

      console.log("Active window:", activeWindowInfo);

      callback = function (rpcResponse, data, rpcRequest) {
        console.log("rpcResponse:", rpcResponse);
        console.log("data:", data);
        console.log("rpcRequest:", rpcRequest);
      };

      OB.RemoteCallManager.call(
        'com.etendoerp.copilot.rest.CopilotContextActionHandler',
        {
          orders: orders,
          activeWindow: activeWindowInfo
        },
        {},
        callback
      );
    },

    buttonType: 'etcop',
    prompt: 'Copilot',

    updateState: function () {
    }
  };

  OB.ToolbarRegistry.registerButton(
    buttonProps.buttonType,
    isc.OBToolbarIconButton,
    buttonProps,
    500
  );
}());