#!/usr/bin/env python3
"""
Pokémon GO Auto Catcher Log Receiver GUI
---------------------------------------
A PyQt5-based GUI application that receives and displays logs from the Android app.

Usage:
    python LogReceiverGUI.py [port]

Default port is 9999 if not specified.
"""

import sys
import socket
import time
import re
import json
import argparse
import threading
from datetime import datetime
from PyQt5.QtWidgets import (QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, 
                            QTextEdit, QPushButton, QLabel, QComboBox, QCheckBox, 
                            QGroupBox, QSplitter, QTabWidget, QLineEdit, QGridLayout,
                            QToolBar, QAction, QStatusBar, QFileDialog, QMessageBox)
from PyQt5.QtCore import Qt, QThread, pyqtSignal, QTimer, QSettings
from PyQt5.QtGui import QColor, QTextCharFormat, QFont, QIcon, QTextCursor

# Default port
DEFAULT_PORT = 9999

# Color mapping for different log categories
CATEGORY_COLORS = {
    "CAPTURE": QColor(0, 200, 0),      # Green
    "ENCOUNTER": QColor(0, 200, 200),  # Cyan
    "MOVEMENT": QColor(0, 100, 255),   # Blue
    "ITEM": QColor(255, 200, 0),       # Yellow
    "NETWORK": QColor(200, 0, 200),    # Magenta
    "GYM": QColor(255, 0, 0),          # Red
    "RAID": QColor(255, 100, 100),     # Light Red
    "POKESTOP": QColor(100, 200, 255), # Light Blue
    "FRIEND": QColor(100, 255, 100),   # Light Green
    "COLLECTION": QColor(255, 100, 255), # Light Magenta
    "INIT": QColor(200, 200, 200),     # Light Gray
    "FRIDA": QColor(255, 255, 255),    # White
    "AR": QColor(100, 100, 255),       # Indigo
    "UNITY": QColor(200, 255, 0),      # Lime
    "FIREBASE": QColor(255, 100, 200), # Pink
    "PHYSICS": QColor(0, 200, 200),    # Teal
    "AUTH": QColor(200, 0, 255),       # Purple
    "D": QColor(200, 200, 200),        # Debug - Light Gray
    "I": QColor(255, 255, 255),        # Info - White
    "W": QColor(255, 200, 0),          # Warning - Yellow
    "E": QColor(255, 0, 0)             # Error - Red
}

# Emoji mapping for different log categories
CATEGORY_EMOJIS = {
    "CAPTURE": "🔴 ",
    "ENCOUNTER": "👀 ",
    "MOVEMENT": "🚶 ",
    "ITEM": "🎒 ",
    "NETWORK": "📡 ",
    "GYM": "🏋️ ",
    "RAID": "⚔️ ",
    "POKESTOP": "🔵 ",
    "FRIEND": "👫 ",
    "COLLECTION": "📚 ",
    "INIT": "🚀 ",
    "FRIDA": "🔧 ",
    "AR": "📷 ",
    "UNITY": "🎮 ",
    "FIREBASE": "🔥 ",
    "PHYSICS": "🧲 ",
    "AUTH": "🔑 ",
    "D": "🔍 ",
    "I": "ℹ️ ",
    "W": "⚠️ ",
    "E": "❌ "
}

class LogReceiverThread(QThread):
    """Thread for receiving logs via UDP."""
    log_received = pyqtSignal(str)
    status_update = pyqtSignal(str)
    
    def __init__(self, port):
        super().__init__()
        self.port = port
        self.running = True
        
    def run(self):
        # Create UDP socket
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        
        # Bind socket to port
        server_address = ('0.0.0.0', self.port)
        self.status_update.emit(f"Starting log receiver on port {self.port}...")
        
        try:
            sock.bind(server_address)
            self.status_update.emit(f"Listening on port {self.port}")
            
            # Set socket to non-blocking mode
            sock.setblocking(False)
            
            while self.running:
                try:
                    # Try to receive data (non-blocking)
                    data, address = sock.recvfrom(4096)
                    message = data.decode('utf-8', errors='replace')
                    
                    # Emit the received message
                    self.log_received.emit(message)
                    
                except BlockingIOError:
                    # No data available, just continue
                    pass
                
                # Small sleep to prevent CPU hogging
                time.sleep(0.01)
                
        except Exception as e:
            self.status_update.emit(f"Error: {str(e)}")
        finally:
            sock.close()
            self.status_update.emit("Log receiver stopped")
    
    def stop(self):
        self.running = False
        self.wait()

class LogReceiverGUI(QMainWindow):
    """Main GUI window for the log receiver."""
    def __init__(self, port=DEFAULT_PORT):
        super().__init__()
        
        self.port = port
        self.log_receiver = None
        self.category_stats = {category: 0 for category in CATEGORY_COLORS.keys()}
        self.active_filters = set()
        self.log_buffer = []
        self.max_log_buffer = 10000  # Maximum number of log entries to keep
        
        self.init_ui()
        self.start_receiver()
        
        # Load settings
        self.load_settings()
        
    def init_ui(self):
        """Initialize the user interface."""
        self.setWindowTitle(f"Pokémon GO Auto Catcher - Log Receiver (Port: {self.port})")
        self.setGeometry(100, 100, 1200, 800)
        
        # Create central widget and layout
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QVBoxLayout(central_widget)
        
        # Create tab widget
        self.tab_widget = QTabWidget()
        main_layout.addWidget(self.tab_widget)
        
        # Create log tab
        log_tab = QWidget()
        log_layout = QVBoxLayout(log_tab)
        
        # Create filter controls
        filter_group = QGroupBox("Log Filters")
        filter_layout = QGridLayout(filter_group)
        
        # Create filter checkboxes
        self.filter_checkboxes = {}
        row, col = 0, 0
        for i, category in enumerate(sorted(CATEGORY_COLORS.keys())):
            checkbox = QCheckBox(category)
            checkbox.setChecked(True)
            checkbox.stateChanged.connect(self.update_filters)
            
            # Set checkbox text color to match category
            color = CATEGORY_COLORS[category]
            style_sheet = f"QCheckBox {{ color: rgb({color.red()}, {color.green()}, {color.blue()}); }}"
            checkbox.setStyleSheet(style_sheet)
            
            self.filter_checkboxes[category] = checkbox
            filter_layout.addWidget(checkbox, row, col)
            
            col += 1
            if col > 4:  # 5 columns
                col = 0
                row += 1
        
        # Add filter controls
        filter_buttons_layout = QHBoxLayout()
        
        self.select_all_button = QPushButton("Select All")
        self.select_all_button.clicked.connect(self.select_all_filters)
        filter_buttons_layout.addWidget(self.select_all_button)
        
        self.clear_all_button = QPushButton("Clear All")
        self.clear_all_button.clicked.connect(self.clear_all_filters)
        filter_buttons_layout.addWidget(self.clear_all_button)
        
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Search logs...")
        self.search_input.textChanged.connect(self.filter_logs)
        filter_buttons_layout.addWidget(self.search_input)
        
        filter_layout.addLayout(filter_buttons_layout, row + 1, 0, 1, 5)
        
        # Create log display
        self.log_display = QTextEdit()
        self.log_display.setReadOnly(True)
        self.log_display.setLineWrapMode(QTextEdit.NoWrap)
        self.log_display.setFont(QFont("Courier New", 10))
        
        # Set dark background for log display
        self.log_display.setStyleSheet("background-color: #000000; color: #FFFFFF;")
        
        # Create splitter for filter and log display
        splitter = QSplitter(Qt.Vertical)
        splitter.addWidget(filter_group)
        splitter.addWidget(self.log_display)
        splitter.setSizes([150, 650])  # Initial sizes
        
        log_layout.addWidget(splitter)
        
        # Create stats tab
        stats_tab = QWidget()
        stats_layout = QVBoxLayout(stats_tab)
        
        self.stats_display = QTextEdit()
        self.stats_display.setReadOnly(True)
        self.stats_display.setFont(QFont("Courier New", 10))
        
        stats_layout.addWidget(self.stats_display)
        
        # Add tabs
        self.tab_widget.addTab(log_tab, "Logs")
        self.tab_widget.addTab(stats_tab, "Statistics")
        
        # Create status bar
        self.status_bar = QStatusBar()
        self.setStatusBar(self.status_bar)
        self.status_bar.showMessage("Ready")
        
        # Create toolbar
        toolbar = QToolBar("Main Toolbar")
        self.addToolBar(toolbar)
        
        # Add actions to toolbar
        start_action = QAction("Start", self)
        start_action.triggered.connect(self.start_receiver)
        toolbar.addAction(start_action)
        
        stop_action = QAction("Stop", self)
        stop_action.triggered.connect(self.stop_receiver)
        toolbar.addAction(stop_action)
        
        clear_action = QAction("Clear Logs", self)
        clear_action.triggered.connect(self.clear_logs)
        toolbar.addAction(clear_action)
        
        save_action = QAction("Save Logs", self)
        save_action.triggered.connect(self.save_logs)
        toolbar.addAction(save_action)
        
        # Set up timer for updating stats
        self.stats_timer = QTimer(self)
        self.stats_timer.timeout.connect(self.update_stats)
        self.stats_timer.start(5000)  # Update every 5 seconds
        
        # Initialize filters
        self.update_filters()
    
    def load_settings(self):
        """Load settings from QSettings."""
        settings = QSettings("PogoAutoCatcher", "LogReceiver")
        
        # Load window geometry
        geometry = settings.value("geometry")
        if geometry:
            self.restoreGeometry(geometry)
        
        # Load filter settings
        for category, checkbox in self.filter_checkboxes.items():
            checked = settings.value(f"filter_{category}", True, type=bool)
            checkbox.setChecked(checked)
    
    def save_settings(self):
        """Save settings to QSettings."""
        settings = QSettings("PogoAutoCatcher", "LogReceiver")
        
        # Save window geometry
        settings.setValue("geometry", self.saveGeometry())
        
        # Save filter settings
        for category, checkbox in self.filter_checkboxes.items():
            settings.setValue(f"filter_{category}", checkbox.isChecked())
    
    def start_receiver(self):
        """Start the log receiver thread."""
        if self.log_receiver is None or not self.log_receiver.isRunning():
            self.log_receiver = LogReceiverThread(self.port)
            self.log_receiver.log_received.connect(self.process_log)
            self.log_receiver.status_update.connect(self.update_status)
            self.log_receiver.start()
    
    def stop_receiver(self):
        """Stop the log receiver thread."""
        if self.log_receiver and self.log_receiver.isRunning():
            self.log_receiver.stop()
            self.log_receiver = None
    
    def update_status(self, message):
        """Update the status bar with a message."""
        self.status_bar.showMessage(message)
    
    def process_log(self, message):
        """Process a received log message."""
        # Update category stats
        trace_match = re.search(r'\[TRACE\]\[[^]]*\]\[([^]]+)\]', message)
        if trace_match:
            category = trace_match.group(1)
            if category in self.category_stats:
                self.category_stats[category] += 1
        
        log_match = re.search(r'\[[^]]*\] \[([^/]+)/[^]]*\]', message)
        if log_match:
            level = log_match.group(1)
            if level in self.category_stats:
                self.category_stats[level] += 1
        
        # Add to log buffer
        self.log_buffer.append(message)
        
        # Trim buffer if it gets too large
        if len(self.log_buffer) > self.max_log_buffer:
            self.log_buffer = self.log_buffer[-self.max_log_buffer:]
        
        # Display if it passes the filter
        if self.should_display(message):
            self.display_log(message)
    
    def should_display(self, message):
        """Check if the message should be displayed based on filters."""
        # Check search filter
        search_text = self.search_input.text().lower()
        if search_text and search_text not in message.lower():
            return False
        
        # Check category filters
        trace_match = re.search(r'\[TRACE\]\[[^]]*\]\[([^]]+)\]', message)
        if trace_match:
            category = trace_match.group(1)
            return category in self.active_filters
        
        log_match = re.search(r'\[[^]]*\] \[([^/]+)/[^]]*\]', message)
        if log_match:
            level = log_match.group(1)
            return level in self.active_filters
        
        # Default: show message if no filter matches
        return True
    
    def display_log(self, message):
        """Format and display a log message in the text edit."""
        cursor = self.log_display.textCursor()
        cursor.movePosition(QTextCursor.End)
        
        # Format based on message type
        trace_match = re.search(r'\[TRACE\]\[(.*?)\]\[(.*?)\](.*)', message)
        if trace_match:
            timestamp = trace_match.group(1)
            category = trace_match.group(2)
            content = trace_match.group(3)
            
            # Format timestamp
            try:
                dt = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
                formatted_time = dt.strftime('%H:%M:%S.%f')[:-3]  # Show only milliseconds
            except:
                formatted_time = timestamp
            
            # Set timestamp format
            time_format = QTextCharFormat()
            time_format.setForeground(QColor(200, 200, 200))
            cursor.insertText(f"[{formatted_time}] ", time_format)
            
            # Set category format
            category_format = QTextCharFormat()
            category_format.setForeground(CATEGORY_COLORS.get(category, QColor(255, 255, 255)))
            emoji = CATEGORY_EMOJIS.get(category, "")
            cursor.insertText(f"{emoji}[{category}] ", category_format)
            
            # Set content format
            content_format = QTextCharFormat()
            content_format.setForeground(QColor(255, 255, 255))
            cursor.insertText(f"{content}\n", content_format)
            
        else:
            # Standard log format
            log_match = re.search(r'\[(.*?)\] \[(.*?)/(.*?)\] (.*)', message)
            if log_match:
                timestamp = log_match.group(1)
                level = log_match.group(2)
                tag = log_match.group(3)
                content = log_match.group(4)
                
                # Set timestamp format
                time_format = QTextCharFormat()
                time_format.setForeground(QColor(200, 200, 200))
                cursor.insertText(f"[{timestamp}] ", time_format)
                
                # Set level/tag format
                level_format = QTextCharFormat()
                level_format.setForeground(CATEGORY_COLORS.get(level, QColor(255, 255, 255)))
                emoji = CATEGORY_EMOJIS.get(level, "")
                cursor.insertText(f"{emoji}[{level}/{tag}] ", level_format)
                
                # Set content format
                content_format = QTextCharFormat()
                content_format.setForeground(QColor(255, 255, 255))
                cursor.insertText(f"{content}\n", content_format)
                
            else:
                # Unknown format, just display as is
                cursor.insertText(f"{message}\n")
        
        # Scroll to bottom
        self.log_display.setTextCursor(cursor)
        self.log_display.ensureCursorVisible()
    
    def update_filters(self):
        """Update the active filters based on checkbox states."""
        self.active_filters = set()
        for category, checkbox in self.filter_checkboxes.items():
            if checkbox.isChecked():
                self.active_filters.add(category)
        
        # Reapply filters to existing logs
        self.filter_logs()
    
    def filter_logs(self):
        """Apply filters to the log buffer and update display."""
        # Clear the log display
        self.log_display.clear()
        
        # Apply filters to the buffer and display matching logs
        for message in self.log_buffer:
            if self.should_display(message):
                self.display_log(message)
    
    def select_all_filters(self):
        """Select all filter checkboxes."""
        for checkbox in self.filter_checkboxes.values():
            checkbox.setChecked(True)
    
    def clear_all_filters(self):
        """Clear all filter checkboxes."""
        for checkbox in self.filter_checkboxes.values():
            checkbox.setChecked(False)
    
    def clear_logs(self):
        """Clear the log display and buffer."""
        self.log_display.clear()
        self.log_buffer.clear()
    
    def save_logs(self):
        """Save logs to a file."""
        filename, _ = QFileDialog.getSaveFileName(
            self, "Save Logs", f"pogo_log_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt",
            "Text Files (*.txt);;All Files (*)"
        )
        
        if filename:
            try:
                with open(filename, 'w', encoding='utf-8') as f:
                    f.write(f"=== Pokémon GO Auto Catcher Log Receiver ===\n")
                    f.write(f"Saved: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
                    
                    for message in self.log_buffer:
                        f.write(f"{message}\n")
                
                self.update_status(f"Logs saved to {filename}")
            except Exception as e:
                QMessageBox.critical(self, "Error", f"Failed to save logs: {str(e)}")
    
    def update_stats(self):
        """Update the statistics display."""
        if self.tab_widget.currentIndex() == 1:  # Stats tab is active
            # Calculate elapsed time
            elapsed = time.time() - self.start_time if hasattr(self, 'start_time') else 0
            hours, remainder = divmod(int(elapsed), 3600)
            minutes, seconds = divmod(remainder, 60)
            elapsed_str = f"{hours:02d}:{minutes:02d}:{seconds:02d}"
            
            # Build stats text
            stats_text = f"=== Pokémon GO Activity Statistics ===\n"
            stats_text += f"Session time: {elapsed_str}\n"
            stats_text += f"Total messages: {sum(self.category_stats.values())}\n\n"
            
            stats_text += f"Category Counts:\n"
            
            # Sort categories by count (descending)
            sorted_stats = sorted(self.category_stats.items(), key=lambda x: x[1], reverse=True)
            
            for category, count in sorted_stats:
                if count > 0:
                    emoji = CATEGORY_EMOJIS.get(category, "")
                    stats_text += f"{emoji}[{category}]: {count}\n"
            
            # Update the stats display
            self.stats_display.setText(stats_text)
    
    def closeEvent(self, event):
        """Handle window close event."""
        # Stop the receiver thread
        self.stop_receiver()
        
        # Save settings
        self.save_settings()
        
        # Accept the close event
        event.accept()

def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description='Pokémon GO Auto Catcher Log Receiver GUI')
    parser.add_argument('port', nargs='?', type=int, default=DEFAULT_PORT,
                        help=f'Port to listen on (default: {DEFAULT_PORT})')
    return parser.parse_args()

def main():
    """Main function."""
    # Parse command line arguments
    args = parse_args()
    
    # Create application
    app = QApplication(sys.argv)
    
    # Create and show main window
    window = LogReceiverGUI(args.port)
    window.show()
    
    # Start the application event loop
    sys.exit(app.exec_())

if __name__ == "__main__":
    main()
