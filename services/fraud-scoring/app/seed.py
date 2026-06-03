"""
Synthetic fraud training cases for pgvector seed.
features_text format must match the describe node in graph.py:
  "Indian payment: INR {amount:.2f} via {network} {method} at MCC {mcc} at {hour:02d}:00 IST"
"""

SEED_CASES: list[dict] = [
    # ── FRAUD ─────────────────────────────────────────────────────────────────
    {
        "features_text": "Indian payment: INR 49800.00 via VISA EMV at MCC 5944 at 02:00 IST",
        "amount_inr": 49800.00, "mcc": "5944", "network": "VISA", "method": "EMV",
        "is_fraud": True, "pattern": "high_risk_mcc_late_night",
    },
    {
        "features_text": "Indian payment: INR 50000.00 via MASTERCARD EMV at MCC 5094 at 03:00 IST",
        "amount_inr": 50000.00, "mcc": "5094", "network": "MASTERCARD", "method": "EMV",
        "is_fraud": True, "pattern": "round_amount_precious_metals",
    },
    {
        "features_text": "Indian payment: INR 100000.00 via VISA EMV at MCC 7995 at 01:00 IST",
        "amount_inr": 100000.00, "mcc": "7995", "network": "VISA", "method": "EMV",
        "is_fraud": True, "pattern": "gambling_extreme_amount_late_night",
    },
    {
        "features_text": "Indian payment: INR 75000.00 via MASTERCARD MAGSTRIPE at MCC 5944 at 14:00 IST",
        "amount_inr": 75000.00, "mcc": "5944", "network": "MASTERCARD", "method": "MAGSTRIPE",
        "is_fraud": True, "pattern": "jewelry_magstripe_fallback_high_amount",
    },
    {
        "features_text": "Indian payment: INR 50000.00 via RUPAY EMV at MCC 7995 at 23:00 IST",
        "amount_inr": 50000.00, "mcc": "7995", "network": "RUPAY", "method": "EMV",
        "is_fraud": True, "pattern": "round_amount_gambling_night",
    },
    {
        "features_text": "Indian payment: INR 12500.00 via VISA EMV at MCC 5094 at 04:00 IST",
        "amount_inr": 12500.00, "mcc": "5094", "network": "VISA", "method": "EMV",
        "is_fraud": True, "pattern": "precious_metals_early_morning",
    },
    {
        "features_text": "Indian payment: INR 85000.00 via VISA EMV at MCC 5311 at 02:00 IST",
        "amount_inr": 85000.00, "mcc": "5311", "network": "VISA", "method": "EMV",
        "is_fraud": True, "pattern": "dept_store_extreme_amount_late_night",
    },
    {
        "features_text": "Indian payment: INR 20000.00 via MASTERCARD EMV at MCC 5944 at 03:00 IST",
        "amount_inr": 20000.00, "mcc": "5944", "network": "MASTERCARD", "method": "EMV",
        "is_fraud": True, "pattern": "jewelry_high_amount_late_night",
    },
    {
        "features_text": "Indian payment: INR 60000.00 via VISA EMV at MCC 7995 at 22:00 IST",
        "amount_inr": 60000.00, "mcc": "7995", "network": "VISA", "method": "EMV",
        "is_fraud": True, "pattern": "gambling_high_late_night",
    },
    {
        "features_text": "Indian payment: INR 30000.00 via RUPAY MAGSTRIPE at MCC 5094 at 02:00 IST",
        "amount_inr": 30000.00, "mcc": "5094", "network": "RUPAY", "method": "MAGSTRIPE",
        "is_fraud": True, "pattern": "precious_metals_magstripe_fallback",
    },
    {
        "features_text": "Indian payment: INR 45000.00 via VISA CONTACTLESS at MCC 5944 at 01:00 IST",
        "amount_inr": 45000.00, "mcc": "5944", "network": "VISA", "method": "CONTACTLESS",
        "is_fraud": True, "pattern": "jewelry_contactless_late_night",
    },
    {
        "features_text": "Indian payment: INR 100000.00 via MASTERCARD EMV at MCC 5311 at 04:00 IST",
        "amount_inr": 100000.00, "mcc": "5311", "network": "MASTERCARD", "method": "EMV",
        "is_fraud": True, "pattern": "extreme_round_amount_early_morning",
    },
    {
        "features_text": "Indian payment: INR 15000.00 via VISA MAGSTRIPE at MCC 7995 at 23:00 IST",
        "amount_inr": 15000.00, "mcc": "7995", "network": "VISA", "method": "MAGSTRIPE",
        "is_fraud": True, "pattern": "gambling_magstripe_fallback_night",
    },
    # ── NOT FRAUD ──────────────────────────────────────────────────────────────
    {
        "features_text": "Indian payment: INR 2500.00 via VISA EMV at MCC 5411 at 11:00 IST",
        "amount_inr": 2500.00, "mcc": "5411", "network": "VISA", "method": "EMV",
        "is_fraud": False, "pattern": "normal_grocery_morning",
    },
    {
        "features_text": "Indian payment: INR 850.00 via MASTERCARD CONTACTLESS at MCC 5812 at 13:00 IST",
        "amount_inr": 850.00, "mcc": "5812", "network": "MASTERCARD", "method": "CONTACTLESS",
        "is_fraud": False, "pattern": "restaurant_lunch_tap",
    },
    {
        "features_text": "Indian payment: INR 450.00 via RUPAY EMV at MCC 5912 at 10:00 IST",
        "amount_inr": 450.00, "mcc": "5912", "network": "RUPAY", "method": "EMV",
        "is_fraud": False, "pattern": "pharmacy_routine_purchase",
    },
    {
        "features_text": "Indian payment: INR 3200.00 via VISA EMV at MCC 5541 at 08:00 IST",
        "amount_inr": 3200.00, "mcc": "5541", "network": "VISA", "method": "EMV",
        "is_fraud": False, "pattern": "petrol_station_morning",
    },
    {
        "features_text": "Indian payment: INR 1800.00 via RUPAY CONTACTLESS at MCC 5411 at 19:00 IST",
        "amount_inr": 1800.00, "mcc": "5411", "network": "RUPAY", "method": "CONTACTLESS",
        "is_fraud": False, "pattern": "grocery_evening_tap",
    },
    {
        "features_text": "Indian payment: INR 6500.00 via MASTERCARD EMV at MCC 5045 at 15:00 IST",
        "amount_inr": 6500.00, "mcc": "5045", "network": "MASTERCARD", "method": "EMV",
        "is_fraud": False, "pattern": "electronics_afternoon_normal",
    },
    {
        "features_text": "Indian payment: INR 720.00 via VISA CONTACTLESS at MCC 5812 at 20:00 IST",
        "amount_inr": 720.00, "mcc": "5812", "network": "VISA", "method": "CONTACTLESS",
        "is_fraud": False, "pattern": "dinner_tap_payment",
    },
    {
        "features_text": "Indian payment: INR 280.00 via RUPAY CONTACTLESS at MCC 5812 at 09:00 IST",
        "amount_inr": 280.00, "mcc": "5812", "network": "RUPAY", "method": "CONTACTLESS",
        "is_fraud": False, "pattern": "coffee_breakfast_tap",
    },
    {
        "features_text": "Indian payment: INR 1200.00 via MASTERCARD EMV at MCC 5912 at 16:00 IST",
        "amount_inr": 1200.00, "mcc": "5912", "network": "MASTERCARD", "method": "EMV",
        "is_fraud": False, "pattern": "medical_pharmacy_afternoon",
    },
    {
        "features_text": "Indian payment: INR 4800.00 via VISA EMV at MCC 5411 at 10:00 IST",
        "amount_inr": 4800.00, "mcc": "5411", "network": "VISA", "method": "EMV",
        "is_fraud": False, "pattern": "supermarket_weekly_shop",
    },
    {
        "features_text": "Indian payment: INR 9800.00 via MASTERCARD EMV at MCC 7011 at 12:00 IST",
        "amount_inr": 9800.00, "mcc": "7011", "network": "MASTERCARD", "method": "EMV",
        "is_fraud": False, "pattern": "hotel_booking_normal",
    },
    {
        "features_text": "Indian payment: INR 500.00 via RUPAY EMV at MCC 5541 at 07:00 IST",
        "amount_inr": 500.00, "mcc": "5541", "network": "RUPAY", "method": "EMV",
        "is_fraud": False, "pattern": "petrol_small_amount_morning",
    },
]
