import requests
from bs4 import BeautifulSoup
import json
import time
from urllib.parse import urljoin

def scrape_single_page(url, headers):
    """
    Scrape a single page of reviews
    """
    try:
        response = requests.get(url, headers=headers, timeout=10)
        response.raise_for_status()
        soup = BeautifulSoup(response.content, 'html.parser')
        
        reviews = []
        
        # Try multiple selectors to find review items
        review_items = soup.find_all('div', class_='review-item')
        
        # If no items found with review-item class, try alternative selectors
        if not review_items:
            # Look for review containers in the main body
            review_body = soup.find('div', id='review-body')
            if review_body:
                review_items = review_body.find_all('div', class_='review-item')
            
            # Another common pattern
            if not review_items:
                review_items = soup.select('.review-item-new')
        
        for item in review_items:
            review_data = {}
            
            # Extract phone name and URL
            title = item.find('h3') or item.find('h2') or item.find('a', class_='review-item-title')
            if title:
                review_data['phone_name'] = title.get_text(strip=True)
                link = title.find('a') if title.name != 'a' else title
                if link and link.get('href'):
                    href = link['href']
                    review_data['review_url'] = urljoin('https://www.gsmarena.com/', href)
            
            # Extract image
            img = item.find('img')
            if img:
                review_data['image_url'] = img.get('src', '')
                review_data['image_alt'] = img.get('alt', '')
            
            # Extract review date
            date_elem = item.find('li') or item.find('span', class_='review-date')
            if date_elem:
                review_data['date'] = date_elem.get_text(strip=True)
            
            # Extract review snippet/description
            snippet = item.find('p')
            if snippet:
                review_data['snippet'] = snippet.get_text(strip=True)
            
            if review_data:  # Only add if we found some data
                reviews.append(review_data)
        
        return reviews, len(response.content) > 1000  # Return True if page has content
        
    except requests.RequestException as e:
        print(f"Error fetching page {url}: {e}")
        return [], False
    except Exception as e:
        print(f"Error parsing page {url}: {e}")
        return [], False


def scrape_gsmarena_reviews(base_url="https://www.gsmarena.com/reviews.php3", 
                            start_page=1, 
                            max_pages=None, 
                            delay=2):
    """
    Scrape phone review data from GSMArena reviews page with pagination support
    
    Args:
        base_url: Base URL (default: https://www.gsmarena.com/reviews.php3)
        start_page: Starting page number (default: 1)
        max_pages: Maximum number of pages to scrape (None = all pages)
        delay: Delay in seconds between page requests (default: 2)
    """
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.5',
        'Accept-Encoding': 'gzip, deflate',
        'Connection': 'keep-alive',
        'Upgrade-Insecure-Requests': '1'
    }
    
    all_reviews = []
    page_num = start_page
    consecutive_empty = 0
    max_consecutive_empty = 3  # Stop after 3 consecutive empty pages
    
    print("Starting pagination scrape...")
    print("=" * 80)
    
    while True:
        # Construct URL with page parameter
        if page_num == 1:
            current_url = base_url
        else:
            current_url = f"{base_url}?iPage={page_num}"
        
        print(f"\nScraping page {page_num}...")
        print(f"URL: {current_url}")
        
        # Scrape current page
        reviews, has_content = scrape_single_page(current_url, headers)
        
        if reviews:
            all_reviews.extend(reviews)
            consecutive_empty = 0  # Reset counter
            print(f"✓ Found {len(reviews)} reviews on page {page_num}")
            print(f"  Total reviews so far: {len(all_reviews)}")
        else:
            consecutive_empty += 1
            print(f"✗ No reviews found on page {page_num}")
            
            if not has_content or consecutive_empty >= max_consecutive_empty:
                print(f"\n{'Page has no content' if not has_content else f'No reviews found for {consecutive_empty} consecutive pages'}. Stopping.")
                break
        
        # Check if we've reached max_pages limit
        if max_pages and (page_num - start_page + 1) >= max_pages:
            print(f"\nReached maximum page limit ({max_pages})")
            break
        
        # Move to next page
        page_num += 1
        
        # Delay before next request
        if delay > 0:
            print(f"Waiting {delay} seconds before next request...")
            time.sleep(delay)
    
    # Print summary
    print("\n" + "=" * 80)
    print(f"\n✓ Scraping complete!")
    print(f"  Total reviews collected: {len(all_reviews)}")
    print(f"  Pages scraped: {page_num - start_page}")
    print("=" * 80)
    
    # Print sample of results
    if all_reviews:
        print(f"\nShowing first 5 reviews:")
        for i, review in enumerate(all_reviews[:5], 1):
            print(f"\n{i}. {review.get('phone_name', 'N/A')}")
            print(f"   Date: {review.get('date', 'N/A')}")
            print(f"   URL: {review.get('review_url', 'N/A')}")
            if review.get('snippet'):
                snippet_text = review.get('snippet')
                print(f"   Snippet: {snippet_text[:100]}{'...' if len(snippet_text) > 100 else ''}")
    
    return all_reviews


def save_to_json(data, filename="gsmarena_reviews.json"):
    """Save scraped data to JSON file"""
    try:
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"\n✓ Data saved to {filename}")
        return True
    except Exception as e:
        print(f"\n✗ Error saving to JSON: {e}")
        return False


def save_to_csv(data, filename="gsmarena_reviews.csv"):
    """Save scraped data to CSV file"""
    try:
        import csv
        if not data:
            return False
            
        keys = data[0].keys()
        with open(filename, 'w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=keys)
            writer.writeheader()
            writer.writerows(data)
        print(f"✓ Data saved to {filename}")
        return True
    except Exception as e:
        print(f"✗ Error saving to CSV: {e}")
        return False


if __name__ == "__main__":
    print("GSMArena Reviews Scraper")
    print("=" * 80)
    
    # Configuration
    BASE_URL = "https://www.gsmarena.com/reviews.php3"
    START_PAGE = 1      # Start from page 1
#    MAX_PAGES = 5       # Set to None to scrape all pages, or a number to limit
    MAX_PAGES = None
    DELAY = 2           # Delay in seconds between requests (be respectful!)
    
    print(f"\nConfiguration:")
    print(f"  Base URL: {BASE_URL}")
    print(f"  Start Page: {START_PAGE}")
    print(f"  Max Pages: {MAX_PAGES if MAX_PAGES else 'All pages'}")
    print(f"  Delay: {DELAY} seconds")
    print()
    
    # Scrape the reviews
    reviews_data = scrape_gsmarena_reviews(
        base_url=BASE_URL,
        start_page=START_PAGE,
        max_pages=MAX_PAGES,
        delay=DELAY
    )
    
    # Save results
    if reviews_data:
        save_to_json(reviews_data)
        save_to_csv(reviews_data)
        print(f"\n{'='*80}")
        print(f"✓ Successfully scraped {len(reviews_data)} reviews from GSMArena!")
        print(f"{'='*80}")
    else:
        print("\n✗ No reviews found or error occurred.")
        print("\nTroubleshooting tips:")
        print("  1. Check your internet connection")
        print("  2. Verify the URL is accessible in your browser")
        print("  3. The website structure may have changed")
        print("  4. Try adjusting the delay or running again later")