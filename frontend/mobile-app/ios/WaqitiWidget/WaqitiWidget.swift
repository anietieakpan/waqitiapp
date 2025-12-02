import WidgetKit
import SwiftUI
import Foundation

// MARK: - Data Models

struct WaqitiWidgetData: Codable, Hashable {
    let balance: String
    let recentTransaction: RecentTransaction?
    let quickActions: [QuickAction]
    let lastUpdated: TimeInterval
    
    struct RecentTransaction: Codable, Hashable {
        let description: String
        let amount: String
        let type: String
        let timestamp: TimeInterval
    }
    
    struct QuickAction: Codable, Hashable {
        let id: String
        let title: String
        let icon: String
        let deeplink: String
    }
    
    static let placeholder = WaqitiWidgetData(
        balance: "$1,234.56",
        recentTransaction: RecentTransaction(
            description: "Coffee Shop",
            amount: "-$5.75",
            type: "sent",
            timestamp: Date().timeIntervalSince1970
        ),
        quickActions: [
            QuickAction(id: "send", title: "Send", icon: "arrow.up.circle", deeplink: "waqiti://send"),
            QuickAction(id: "request", title: "Request", icon: "arrow.down.circle", deeplink: "waqiti://request"),
            QuickAction(id: "scan", title: "Scan", icon: "qrcode", deeplink: "waqiti://scan"),
            QuickAction(id: "history", title: "History", icon: "clock", deeplink: "waqiti://transactions")
        ],
        lastUpdated: Date().timeIntervalSince1970
    )
}

// MARK: - Widget Timeline Provider

struct WaqitiWidgetProvider: TimelineProvider {
    typealias Entry = WaqitiWidgetEntry
    
    func placeholder(in context: Context) -> WaqitiWidgetEntry {
        WaqitiWidgetEntry(date: Date(), data: WaqitiWidgetData.placeholder)
    }
    
    func getSnapshot(in context: Context, completion: @escaping (WaqitiWidgetEntry) -> Void) {
        let entry = WaqitiWidgetEntry(date: Date(), data: loadWidgetData() ?? WaqitiWidgetData.placeholder)
        completion(entry)
    }
    
    func getTimeline(in context: Context, completion: @escaping (Timeline<WaqitiWidgetEntry>) -> Void) {
        let currentData = loadWidgetData() ?? WaqitiWidgetData.placeholder
        let entry = WaqitiWidgetEntry(date: Date(), data: currentData)
        
        // Update every 15 minutes
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date()
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        
        completion(timeline)
    }
    
    private func loadWidgetData() -> WaqitiWidgetData? {
        guard let sharedDefaults = UserDefaults(suiteName: "group.com.waqiti.app"),
              let data = sharedDefaults.data(forKey: "widget_data") else {
            return nil
        }
        
        do {
            return try JSONDecoder().decode(WaqitiWidgetData.self, from: data)
        } catch {
            print("Failed to decode widget data: \(error)")
            return nil
        }
    }
}

// MARK: - Widget Entry

struct WaqitiWidgetEntry: TimelineEntry {
    let date: Date
    let data: WaqitiWidgetData
}

// MARK: - Widget Views

struct WaqitiBalanceWidgetView: View {
    let entry: WaqitiWidgetEntry
    @Environment(\.widgetFamily) var family
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                gradient: Gradient(colors: [Color.blue.opacity(0.8), Color.purple.opacity(0.6)]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            
            VStack(spacing: 12) {
                // Header
                HStack {
                    Image(systemName: "wallet.pass")
                        .foregroundColor(.white)
                        .font(.title2)
                    
                    Text("Waqiti")
                        .font(.headline)
                        .foregroundColor(.white)
                        .fontWeight(.semibold)
                    
                    Spacer()
                    
                    Text(timeAgoString(from: Date(timeIntervalSince1970: entry.data.lastUpdated)))
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.8))
                }
                
                // Balance
                VStack(alignment: .leading, spacing: 4) {
                    Text("Total Balance")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    
                    Text(entry.data.balance)
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .minimumScaleFactor(0.7)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                
                // Recent transaction (medium/large widgets only)
                if family != .systemSmall, let transaction = entry.data.recentTransaction {
                    Divider()
                        .background(Color.white.opacity(0.3))
                    
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Recent")
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.8))
                            
                            Text(transaction.description)
                                .font(.caption)
                                .foregroundColor(.white)
                                .lineLimit(1)
                        }
                        
                        Spacer()
                        
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(transaction.amount)
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(transaction.type == "sent" ? .red.opacity(0.8) : .green.opacity(0.8))
                            
                            Text(timeAgoString(from: Date(timeIntervalSince1970: transaction.timestamp)))
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.6))
                        }
                    }
                }
                
                // Quick actions (large widget only)
                if family == .systemLarge {
                    Divider()
                        .background(Color.white.opacity(0.3))
                    
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 2), spacing: 8) {
                        ForEach(Array(entry.data.quickActions.prefix(4)), id: \.id) { action in
                            Link(destination: URL(string: action.deeplink)!) {
                                HStack {
                                    Image(systemName: iconName(for: action.icon))
                                        .foregroundColor(.white)
                                        .font(.caption)
                                    
                                    Text(action.title)
                                        .font(.caption)
                                        .foregroundColor(.white)
                                }
                                .padding(8)
                                .background(Color.white.opacity(0.2))
                                .cornerRadius(8)
                            }
                        }
                    }
                }
            }
            .padding()
        }
        .cornerRadius(16)
        .shadow(radius: 4)
        .widgetURL(URL(string: "waqiti://home"))
    }
    
    private func timeAgoString(from date: Date) -> String {
        let interval = Date().timeIntervalSince(date)
        
        if interval < 60 {
            return "now"
        } else if interval < 3600 {
            let minutes = Int(interval / 60)
            return "\(minutes)m"
        } else if interval < 86400 {
            let hours = Int(interval / 3600)
            return "\(hours)h"
        } else {
            let days = Int(interval / 86400)
            return "\(days)d"
        }
    }
    
    private func iconName(for icon: String) -> String {
        let iconMapping: [String: String] = [
            "arrow-up-circle": "arrow.up.circle",
            "arrow-down-circle": "arrow.down.circle",
            "qr-code-scanner": "qrcode",
            "credit-card": "creditcard",
            "history": "clock",
            "bitcoin": "bitcoinsign.circle",
            "group": "person.3",
            "add-circle": "plus.circle"
        ]
        return iconMapping[icon] ?? "questionmark.circle"
    }
}

struct WaqitiQuickActionsWidgetView: View {
    let entry: WaqitiWidgetEntry
    
    var body: some View {
        ZStack {
            // Background
            LinearGradient(
                gradient: Gradient(colors: [Color.blue.opacity(0.6), Color.cyan.opacity(0.4)]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            
            VStack(spacing: 8) {
                // Header
                HStack {
                    Image(systemName: "bolt.circle")
                        .foregroundColor(.white)
                        .font(.title3)
                    
                    Text("Quick Actions")
                        .font(.subheadline)
                        .foregroundColor(.white)
                        .fontWeight(.medium)
                    
                    Spacer()
                }
                
                // Actions grid
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 2), spacing: 6) {
                    ForEach(Array(entry.data.quickActions.prefix(4)), id: \.id) { action in
                        Link(destination: URL(string: action.deeplink)!) {
                            VStack(spacing: 4) {
                                Image(systemName: iconName(for: action.icon))
                                    .foregroundColor(.white)
                                    .font(.title3)
                                
                                Text(action.title)
                                    .font(.caption2)
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(8)
                            .background(Color.white.opacity(0.2))
                            .cornerRadius(8)
                        }
                    }
                }
            }
            .padding()
        }
        .cornerRadius(16)
        .shadow(radius: 2)
    }
    
    private func iconName(for icon: String) -> String {
        let iconMapping: [String: String] = [
            "arrow-up-circle": "arrow.up.circle",
            "arrow-down-circle": "arrow.down.circle",
            "qr-code-scanner": "qrcode",
            "credit-card": "creditcard",
            "history": "clock",
            "bitcoin": "bitcoinsign.circle",
            "group": "person.3",
            "add-circle": "plus.circle"
        ]
        return iconMapping[icon] ?? "questionmark.circle"
    }
}

// MARK: - Widget Configuration

struct WaqitiBalanceWidget: Widget {
    let kind: String = "WaqitiBalanceWidget"
    
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WaqitiWidgetProvider()) { entry in
            WaqitiBalanceWidgetView(entry: entry)
        }
        .configurationDisplayName("Waqiti Balance")
        .description("View your balance and recent transactions at a glance")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct WaqitiQuickActionsWidget: Widget {
    let kind: String = "WaqitiQuickActionsWidget"
    
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WaqitiWidgetProvider()) { entry in
            WaqitiQuickActionsWidgetView(entry: entry)
        }
        .configurationDisplayName("Waqiti Quick Actions")
        .description("Quick access to common Waqiti actions")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Widget Bundle

@main
struct WaqitiWidgetBundle: WidgetBundle {
    var body: some Widget {
        WaqitiBalanceWidget()
        WaqitiQuickActionsWidget()
    }
}