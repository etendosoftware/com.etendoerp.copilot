/* jslint */
// Create the Navbar Button
isc.Button.create({
  baseStyle: 'navBarButton',
  title: "Copilot",
  class: "OBNavBarTextButton",
  textColor: "#FFFFFF",
  overflow: "visible",
  height: 30,
  width: 80,
  layoutAlign: "center",
  showRollOver: false,
  showFocused: false,
  showDown: false,
  icon: "/etendo/web/images/AI.png",
  borderRadius: "8px !important",
  iconWidth: 14,
  iconHeight: 14,

  // Event handler for button click
  click: function() {
        // Define constants for commonly used values
        WHITE_COLOR = "#FFFFFF"
        GRAY_COLOR = "#666666"
        BUTTON_WIDTH = 80
        BUTTON_HEIGHT = 30
        WINDOW_WIDTH = 380
        WINDOW_HEIGHT = 450

        // Create a new window for Copilot
        myWindow = isc.Window.create({
        width: WINDOW_WIDTH,
        baseStyle: 'widgetContainer',
        height: WINDOW_HEIGHT,
        left: isc.Page.getWidth() - WINDOW_WIDTH,
        top: isc.Page.getHeight() - WINDOW_HEIGHT,
        canDragReposition: true,
        headerProperties: {
            height: "0px",
        },
        backgroundColor: WHITE_COLOR,
        borderRadius: "20px",
        items: [
                isc.HTMLPane.create({
                  width: "100%",
                  height: "100%",
                  contents: `
                    <html>
                      <head>
                        <style>
                            .close-button {
                              cursor: pointer;
                              width: 1.3rem;
                              height: 1.3rem;
                            }
                            .copilot-logotype {
                              width: 2.5rem;
                              height: 2.5rem;
                            }
                            .container-header {
                                display: flex;
                                justify-content: space-between;
                                margin: 0 12px;
                                align-items: center;
                                background-color: WHITE_COLOR;
                            }
                            .content-header {
                                display: flex;
                                gap: 0.75rem;
                                align-items: center;
                                padding-top: 0.5rem;
                                padding-bottom: 0.5rem;
                            }
                            .copilot-title {
                                font-size: 1.3rem;
                                font-weight: bold;
                                color: GRAY_COLOR;
                            }
                         </style>
                         <script src="web/js/copilot.js"></script>
                      </head>
                      <body>
                        <div class="container-header">
                            <div class="content-header">
                                <img class="copilot-logotype" src="web/images/copilot.png" alt="Logo Copilot">
                                <span class="copilot-title">Copilot</span>
                            </div>
                            <img class="close-button" onclick="window.parent.myWindow.close()" src="web/images/Close.png" alt="Botón Cerrar">
                        </div>
                        <iframe width="100%" height="88.75%" src="web/com.etendoerp.copilot.dist" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
                      </body>
                    </html>
                  `
                })
              ],
    })
    myWindow.show();
  }
})
