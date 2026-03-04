import requests
from bs4 import BeautifulSoup
import json
import time
import csv
import re
from urllib.parse import urljoin

def find_spec_url_from_review(review_url, headers):
    """
    Find the specifications URL from a review page
    """
    try:
        response = requests.get(review_url, headers=headers, timeout=15)
        response.raise_for_status()
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # Look for dropdown menu or links to specifications
        # Common patterns: "Full specifications", "Specifications", etc.
        spec_link = None
        
        # Try to find the spec link in navigation or dropdown
        links = soup.find_all('a', href=True)
        for link in links:
            href = link['href']
            text = link.get_text(strip=True).lower()
            
            # Look for specification links
            if 'specification' in text or 'specs' in text or 'full phone' in text:
                if '.php' in href:
                    spec_link = urljoin('https://www.gsmarena.com/', href)
                    break
        
        # Alternative: Extract phone model from review URL and construct spec URL
        # Review URL pattern: infinix_hot_60_pro_plus-review-2868.php
        # Spec URL pattern: infinix_hot_60_pro+-14002.php
        if not spec_link:
            # Try to find phone name link
            for link in links:
                href = link['href']
                # Look for phone specification page pattern (ends with -XXXX.php)
                if re.search(r'-\d{4,5}\.php', href) and 'review' not in href:
                    spec_link = urljoin('https://www.gsmarena.com/', href)
                    break
        
        return spec_link
        
    except Exception as e:
        print(f"  ✗ Error finding spec URL: {e}")
        return None


def scrape_specifications(spec_url, headers):
    """
    Scrape all specifications from a phone specification page
    """
    try:
        response = requests.get(spec_url, headers=headers, timeout=15)
        response.raise_for_status()
        soup = BeautifulSoup(response.content, 'html.parser')
        
        specifications = {}
        
        # Find the phone name/title
        phone_title = soup.find('h1', class_='specs-phone-name-title')
        if phone_title:
            specifications['phone_name'] = phone_title.get_text(strip=True)
        
        # Find all specification tables
        spec_tables = soup.find_all('table')
        
        for table in spec_tables:
            # Get category header (e.g., "Network", "Body", "Display")
            category_header = table.find_previous('th')
            if category_header:
                category = category_header.get_text(strip=True)
            else:
                category = "General"
            
            # Initialize category in specifications if not exists
            if category not in specifications:
                specifications[category] = {}
            
            # Extract all rows in the table
            rows = table.find_all('tr')
            
            for row in rows:
                cells = row.find_all('td')
                
                if len(cells) >= 2:
                    # First cell is the spec name, second is the value
                    spec_name = cells[0].get_text(strip=True)
                    spec_value = cells[1].get_text(strip=True)
                    
                    # Clean up the spec name (remove links text, icons, etc.)
                    spec_name = re.sub(r'\s+', ' ', spec_name)
                    spec_value = re.sub(r'\s+', ' ', spec_value)
                    
                    if spec_name and spec_value:
                        specifications[category][spec_name] = spec_value
        
        # Alternative parsing method if the above doesn't work
        if len(specifications) <= 1:
            # Look for spec list items
            spec_list = soup.find('div', id='specs-list')
            if spec_list:
                categories = spec_list.find_all('table')
                
                for table in categories:
                    # Get the category name from the header
                    header = table.find('th')
                    if header:
                        category = header.get_text(strip=True)
                        specifications[category] = {}
                        
                        # Get all spec rows
                        rows = table.find_all('tr')
                        for row in rows:
                            cells = row.find_all('td', class_='ttl')
                            values = row.find_all('td', class_='nfo')
                            
                            if cells and values:
                                for cell, value in zip(cells, values):
                                    spec_name = cell.get_text(strip=True)
                                    spec_value = value.get_text(strip=True)
                                    
                                    if spec_name and spec_value:
                                        specifications[category][spec_name] = spec_value
        
        return specifications
        
    except Exception as e:
        print(f"  ✗ Error scraping specifications: {e}")
        return None


def scrape_specs_from_csv(csv_file, output_file="gsmarena_specifications.json",
                          max_phones=None, delay=2, start_from=0):
    """
    Read review URLs from CSV and scrape specifications for each phone
    
    Args:
        csv_file: Path to CSV file with review_url column
        output_file: Output JSON file path
        max_phones: Maximum number of phones to scrape (None = all)
        delay: Delay in seconds between requests
        start_from: Start from this phone index (for resuming)
    """
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.5',
        'Connection': 'keep-alive',
    }
    
    # Read CSV file
    print(f"Reading review URLs from {csv_file}...")
    phone_data = []
    
    try:
        with open(csv_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                if 'review_url' in row and row['review_url']:
                    phone_data.append({
                        'phone_name': row.get('phone_name', 'Unknown'),
                        'review_url': row['review_url'],
                        'date': row.get('date', 'Unknown')
                    })
    except Exception as e:
        print(f"✗ Error reading CSV: {e}")
        return []
    
    print(f"Found {len(phone_data)} phones in CSV")
    
    # Apply filters
    if start_from > 0:
        phone_data = phone_data[start_from:]
        print(f"Starting from phone #{start_from + 1}")
    
    if max_phones:
        phone_data = phone_data[:max_phones]
        print(f"Limiting to {max_phones} phones")
    
    print("=" * 80)
    
    # Scrape specifications for each phone
    all_specifications = []
    
    for idx, phone_info in enumerate(phone_data, 1):
        print(f"\n[{idx}/{len(phone_data)}] {phone_info['phone_name']}")
        print(f"  Review URL: {phone_info['review_url']}")
        
        # Find specification URL
        spec_url = find_spec_url_from_review(phone_info['review_url'], headers)
        
        if spec_url:
            print(f"  ✓ Found spec URL: {spec_url}")
            
            # Scrape specifications
            specifications = scrape_specifications(spec_url, headers)
            
            if specifications:
                # Add metadata
                specifications['_metadata'] = {
                    'phone_name': phone_info['phone_name'],
                    'review_url': phone_info['review_url'],
                    'spec_url': spec_url,
                    'date': phone_info['date']
                }
                
                all_specifications.append(specifications)
                
                # Count total specs
                total_specs = sum(len(v) for k, v in specifications.items() if isinstance(v, dict))
                print(f"  ✓ Extracted {len(specifications)-1} categories with {total_specs} specifications")
                
                # Save progress after each phone
                save_to_json(all_specifications, output_file)
            else:
                print(f"  ✗ Failed to scrape specifications")
        else:
            print(f"  ✗ Could not find specification URL")
        
        # Delay before next request
        if idx < len(phone_data):
            print(f"  Waiting {delay} seconds...")
            time.sleep(delay)
    
    print("\n" + "=" * 80)
    print(f"\n✓ Scraping complete!")
    print(f"  Total phones scraped: {len(all_specifications)}")
    print("=" * 80)
    
    return all_specifications


def save_to_json(data, filename):
    """Save data to JSON file"""
    try:
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        return True
    except Exception as e:
        print(f"✗ Error saving JSON: {e}")
        return False


def flatten_specs_for_csv(specs_data):
    """
    Flatten specifications into a format suitable for CSV
    """
    flattened = []
    
    for phone_specs in specs_data:
        row = {}
        
        # Add metadata
        if '_metadata' in phone_specs:
            row.update(phone_specs['_metadata'])
        
        # Flatten all specification categories
        for category, specs in phone_specs.items():
            if category != '_metadata' and isinstance(specs, dict):
                for spec_name, spec_value in specs.items():
                    # Create column name as "Category - Spec Name"
                    column_name = f"{category} - {spec_name}"
                    row[column_name] = spec_value
        
        flattened.append(row)
    
    return flattened


def save_specs_to_csv(data, filename="gsmarena_specifications.csv"):
    """Save specifications to CSV file"""
    try:
        if not data:
            return False
        
        # Flatten the nested specifications
        flattened_data = flatten_specs_for_csv(data)
        
        if not flattened_data:
            return False
        
        # Get all unique column names
        all_columns = set()
        for row in flattened_data:
            all_columns.update(row.keys())
        
        # Sort columns: metadata first, then alphabetically
        metadata_cols = ['phone_name', 'date', 'review_url', 'spec_url']
        spec_cols = sorted([col for col in all_columns if col not in metadata_cols])
        columns = [col for col in metadata_cols if col in all_columns] + spec_cols
        
        # Write to CSV
        with open(filename, 'w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=columns)
            writer.writeheader()
            writer.writerows(flattened_data)
        
        print(f"✓ Specifications saved to {filename}")
        print(f"  Columns: {len(columns)}")
        return True
        
    except Exception as e:
        print(f"✗ Error saving CSV: {e}")
        return False


if __name__ == "__main__":
    print("GSMArena Specifications Scraper")
    print("=" * 80)
    
    # Configuration
    INPUT_CSV = "gsmarena_reviews.csv"
    OUTPUT_JSON = "gsmarena_specifications.json"
    OUTPUT_CSV = "gsmarena_specifications.csv"
    MAX_PHONES = None       # Set to None to scrape all phones
    DELAY = 2            # Delay in seconds between requests
    START_FROM = 0       # Start from this index
    
    print(f"\nConfiguration:")
    print(f"  Input CSV: {INPUT_CSV}")
    print(f"  Output JSON: {OUTPUT_JSON}")
    print(f"  Output CSV: {OUTPUT_CSV}")
    print(f"  Max Phones: {MAX_PHONES if MAX_PHONES else 'All'}")
    print(f"  Delay: {DELAY} seconds")
    print(f"  Start From: Phone #{START_FROM + 1}")
    print()
    
    # Scrape specifications
    specifications = scrape_specs_from_csv(
        csv_file=INPUT_CSV,
        output_file=OUTPUT_JSON,
        max_phones=MAX_PHONES,
        delay=DELAY,
        start_from=START_FROM
    )
    
    # Save to both formats
    if specifications:
        save_to_json(specifications, OUTPUT_JSON)
        save_specs_to_csv(specifications, OUTPUT_CSV)
        print(f"\n{'='*80}")
        print(f"✓ Successfully scraped specifications for {len(specifications)} phones!")
        print(f"{'='*80}")
    else:
        print("\n✗ No specifications scraped.")
