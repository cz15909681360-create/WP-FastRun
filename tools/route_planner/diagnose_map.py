"""Run a bounded map startup check without printing stored credentials."""
import json
import re
import sys
from PySide6.QtCore import QTimer, Qt, qInstallMessageHandler
from PySide6.QtWidgets import QApplication
from native_app import App


def redact(text):
    return re.sub(r'(?i)(key|sig|sk)=([^&\s\"\'<>]+)', r'\1=[redacted]', text)


def main():
    qInstallMessageHandler(lambda kind, context, message: print(redact(message), flush=True))
    app = QApplication(sys.argv)
    window = App()
    window.setAttribute(Qt.WidgetAttribute.WA_DontShowOnScreen)
    window.resize(1280, 800)
    window.show()
    print(json.dumps({'hasKey': bool(window.map.key), 'hasSK': bool(window.map.sk)}), flush=True)
    exit_code = [1]
    probe = """JSON.stringify({bridgeLibrary:typeof QWebChannel,transport:typeof qt,
    tmap:typeof TMap,mapReady:typeof map!=='undefined'&&!!map,
    message:document.getElementById('error')?.textContent,
    errorVisible:document.getElementById('error')?.style.display!=='none',
    overlaysReady:typeof markers!=='undefined'&&!!markers&&!!lines,
    canvas:document.querySelectorAll('canvas').length,
    size:[innerWidth,innerHeight]})"""

    def finish(result):
        print(redact(str(result)), flush=True)
        try:
            state = json.loads(result)
            exit_code[0] = 0 if state['mapReady'] and state['overlaysReady'] and not state['errorVisible'] else 1
        except (TypeError, ValueError, KeyError):
            pass
        if len(sys.argv)>1:
            window.grab().save(sys.argv[1])
        window.map.shutdown()
        app.quit()

    QTimer.singleShot(12000, lambda: window.map.page().runJavaScript(probe, finish))
    QTimer.singleShot(17000, app.quit)
    app.exec()
    return exit_code[0]


if __name__ == '__main__':
    sys.exit(main())
